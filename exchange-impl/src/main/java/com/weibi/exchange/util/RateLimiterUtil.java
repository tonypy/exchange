package com.weibi.exchange.util;

import com.google.common.util.concurrent.RateLimiter;
import com.weibi.config.enums.TradeOrderSrcEnum;
import com.weibi.core.commons.logging.ILogging;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 限流工具类
 */
@Component
@RefreshScope
    public class RateLimiterUtil implements ILogging {
    /***速率限流***/
    private static final int RATE_LIMIT = 1;
    /***指标限流***/
    private static final int METRIC_LIMIT = 0;
    /***限流器***/
    private static final ConcurrentHashMap<String, CustRateLimiter> concurrentHashMap = new ConcurrentHashMap();
    /***下单-来源限流***/
    private static final String EXCHANGE_SRC_CREATE_LIMI = "exchange:src:create:%s";
    /***下单-来源-币对限流***/
    private static final String EXCHANGE_SRC_CREATE_PAIR_LIMI = "exchange:create:pair:%s:%s";
    /***撤单-来源限流***/
    private static final String EXCHANGE_SRC_CANCEL_LIMI = "exchange:src:cancel:%s";
    /***撤单-来源-币对限流***/
    private static final String EXCHANGE_SRC_CANCEL_PAIR_LIMI = "exchange:src:cancel:pair:%s:%s";
    @Value("${exchange.create.srcUserPairLimit:10}")
    private String createSrcUserPairLimit;
    @Value("${exchange.create.srcOpenApiPairLimit:10}")
    private String createSrcOpenApiPairLimit;
    @Value("${exchange.create.srcMachinePairLimit:5}")
    private String createSrcMachinePairLimit;
    @Value("${exchange.create.srcUserLimit:50}")
    private String createSrcUserLimit;
    @Value("${exchange.create.srcOpenApiLimit:100}")
    private String createSrcOpenApiLimit;
    @Value("${exchange.create.srcMachineLimit:100}")
    private String createSrcMachineLimit;
    @Value("${exchange.cancel.srcUserPairLimit:10}")
    private String cancelSrcUserPairLimit;
    @Value("${exchange.cancel.srcOpenApiPairLimit:10}")
    private String cancelSrcOpenApiPairLimit;
    @Value("${exchange.cancel.srcMachinePairLimit:5}")
    private String cancelSrcMachinePairLimit;
    @Value("${exchange.cancel.srcUserPairLimit:50}")
    private String cancelSrcUserLimit;
    @Value("${exchange.cancel.srcOpenApiLimit:100}")
    private String cancelSrcOpenApiLimit;
    @Value("${exchange.cancel.srcMachineLimit:100}")
    private String cancelSrcMachineLimit;

    /**
     * 下单限流
     *
     * @param src
     * @param pair
     * @return 0 =正常 1=币对限流 2 来源限流
     */
    public int tryAcquireByCreate(String src, String pair) {
        if (!tryAcquireByPairCreate(src, pair)) {
            return 1;
        } else if (!tryAcquireBySrcCreate(getSrcKey(src))) {
            return 2;
        }
        return 0;
    }

    /**
     * 撤单限流
     *
     * @param src
     * @param pair
     * @return 0 =正常 1=币对限流 2 来源限流
     */
    public int tryAcquireByCancel(String src, String pair) {
        if (!tryAcquireByPairCancel(src, pair)) {
            return 1;
        } else if (!tryAcquireBySrcCancel(getSrcKey(src))) {
            return 2;
        }
        return 0;
    }

    /**
     * 下单根据来源限流
     *
     * @param src
     * @return
     */
    private boolean tryAcquireBySrcCreate(String src) {
        int limitType = METRIC_LIMIT;
        int limit = getLimitBySrcAndPair(src, null, true);
        return tryAcquireInter(limitType, String.format(EXCHANGE_SRC_CREATE_LIMI, src), limit);
    }

    /**
     * 下单根据币对限流
     *
     * @param src
     * @param pair
     * @return
     */
    private boolean tryAcquireByPairCreate(String src, String pair) {
        int limitType = METRIC_LIMIT;
        int limit = getLimitBySrcAndPair(src, pair, true);
        return StringUtils.isEmpty(pair) || tryAcquireInter(limitType, String.format(EXCHANGE_SRC_CREATE_PAIR_LIMI, src, pair), limit);
    }

    /**
     * 撤单根据来源限流
     *
     * @param src
     * @return
     */
    private boolean tryAcquireBySrcCancel(String src) {
        String key = getSrcKey(src);
        int limitType = getLimitTypeBySrc(src);
        int limit = getLimitBySrcAndPair(src, null, false);
        return tryAcquireInter(limitType, String.format(EXCHANGE_SRC_CANCEL_LIMI, key), limit);
    }

    /**
     * 撤单根据币对限流
     *
     * @param src
     * @param pair
     * @return
     */
    private boolean tryAcquireByPairCancel(String src, String pair) {
        int limitType = getLimitTypeBySrc(src);
        int limit = getLimitBySrcAndPair(src, pair, false);
        return StringUtils.isEmpty(pair) || tryAcquireInter(limitType, String.format(EXCHANGE_SRC_CANCEL_PAIR_LIMI, src, pair), limit);
    }

    /**
     * 获取限流
     *
     * @param type
     * @param key
     * @param maxCounter
     * @return
     */
    private boolean tryAcquireInter(int type, String key, int maxCounter) {
        CustRateLimiter rateLimiter = concurrentHashMap.get(key);
        if (rateLimiter == null) {
            rateLimiter = getCustRateLimiter(type, key, maxCounter);
        }
        if (rateLimiter.getMaxCounter() != maxCounter) {
            error("刷新限流数量:<{}><{}><{}>", key, rateLimiter.getMaxCounter(), maxCounter);
            rateLimiter = getCustRateLimiter(type, key, maxCounter);
        }
        return rateLimiter.tryAcquire();
    }

    /**
     * 根据来源构建key
     *
     * @param src
     * @return
     */
    private String getSrcKey(String src) {
        return TradeOrderSrcEnum.INTER.getCode().equalsIgnoreCase(src)
                || TradeOrderSrcEnum.OPENAPI.getCode().equalsIgnoreCase(src) ? src : "USER";
    }

    /**
     * 根据来源获取限流器
     *
     * @param src
     * @return
     */
    private int getLimitTypeBySrc(String src) {
        return TradeOrderSrcEnum.INTER.getCode().equalsIgnoreCase(src)
                || TradeOrderSrcEnum.OPENAPI.getCode().equalsIgnoreCase(src) ? RATE_LIMIT : METRIC_LIMIT;
    }

    /**
     * 根据币对或者来源获取限流值
     *
     * @param src
     * @param pair
     * @return
     */
    private int getLimitBySrcAndPair(String src, String pair, boolean isCreateOrder) {
        String limit = "0";
        if (TradeOrderSrcEnum.INTER.getCode().equalsIgnoreCase(src)) {
            limit = StringUtils.isEmpty(pair) ?
                    (isCreateOrder ? createSrcMachineLimit : cancelSrcMachineLimit)
                    : (isCreateOrder ? createSrcMachinePairLimit : cancelSrcMachinePairLimit);
        } else if (TradeOrderSrcEnum.OPENAPI.getCode().equalsIgnoreCase(src)) {
            limit = StringUtils.isEmpty(pair) ?
                    (isCreateOrder ? createSrcOpenApiLimit : cancelSrcOpenApiLimit)
                    : (isCreateOrder ? createSrcOpenApiPairLimit : cancelSrcOpenApiPairLimit);
        } else {
            limit = StringUtils.isEmpty(pair) ?
                    (isCreateOrder ? createSrcUserLimit : cancelSrcUserLimit)
                    : (isCreateOrder ? createSrcUserPairLimit : cancelSrcUserPairLimit);
        }
        return Integer.valueOf(limit);
    }

    private synchronized CustRateLimiter getCustRateLimiter(int type, String key, int maxCounter) {
        CustRateLimiter r = new CustRateLimiter(type, maxCounter);
        concurrentHashMap.put(key, r);
        return r;
    }

    /**
     * 简单限流计数器
     */
    @Data
    static class CustRateLimiter {
        private int maxCounter;
        private volatile int counter;
        private volatile long period;
        private Lock lock;
        private RateLimiter rateLimiter;
        private int type;

        /**
         * @param type       0 = 资源限流 1=平滑限流
         * @param maxCounter
         */
        public CustRateLimiter(int type, int maxCounter) {
            this.maxCounter = maxCounter;
            this.counter = maxCounter;
            this.period = getCurrPeriod();
            this.type = type;
            if (type == METRIC_LIMIT) {
                this.lock = new ReentrantLock();
            } else {
                rateLimiter = RateLimiter.create(maxCounter);
            }
        }

        public boolean tryAcquire() {
            if (type == METRIC_LIMIT) {
                lock.lock();
                try {
                    long currPeriod = getCurrPeriod();
                    if (currPeriod != this.period) {
                        this.period = currPeriod;
                        this.counter = this.maxCounter;
                    }
                    --this.counter;
                    return this.counter >= 0;
                } finally {
                    lock.unlock();
                }
            } else {
                return rateLimiter.tryAcquire();
            }
        }

        private long getCurrPeriod() {
            return System.currentTimeMillis() / 1000;
        }
    }
}
