package com.weibi.exchange.util;

import com.alibaba.fastjson.JSON;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.weibi.config.enums.TradeOrderSrcEnum;
import com.weibi.config.enums.TradeOrderStatusEnum;
import com.weibi.config.enums.TradeOrderTypeEnum;
import com.weibi.config.model.trade.TradeOrder;
import com.weibi.config.util.TradeModelUtil;
import com.weibi.core.balance.delega.model.BalanceChangeRecord;
import com.weibi.core.balance.delega.model.BalanceChangeRequest;
import com.weibi.exchange.core.ProcessOrderTypeEnum;
import com.weibi.exchange.core.TradeOrderCmd;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import static com.weibi.core.balance.delega.utils.UserAccountsUtils.mainKeyFromWalletTypeAndSubKey;
import static com.weibi.core.balance.delega.utils.UserAccountsUtils.subKeyForNonMargin;

/**
 * 业务工具类
 *
 * @author jsong
 */
@Slf4j
public class BizUtil {
    /***撮合warn相关log***/
    public static final Logger ACCOUNT_WARN_LOG = LoggerFactory.getLogger("ACCOUNT_WARN_LOG");
    /***撮合warn相关log***/
    public static final Logger EXCHANGE_WARN_LOG = LoggerFactory.getLogger("EXCHANGE_WARN_LOG");
    /***业务warn相关log***/
    public static final Logger BIZ_WARN_LOG = LoggerFactory.getLogger("BIZ_WARN_LOG");
    /***业务线程名字***/
    private static ThreadLocal<String> BIZ_THREAD_NAME = new ThreadLocal<>();
    /***原子类***/
    private static AtomicLong ATOMIC_LONG = new AtomicLong();

    /**
     * 是否是市价单
     *
     * @param tradeOrder
     * @return
     */
    public static boolean isMarketOrder(TradeOrder tradeOrder) {
        return StringUtils.equals(TradeOrderTypeEnum.MARKET.getCode(), tradeOrder.getOrderType());
    }

    /**
     * 是否是计划委托单
     *
     * @param tradeOrder
     * @return
     */
    public static boolean isStopLimitOrder(TradeOrder tradeOrder) {
        return StringUtils.equals(TradeOrderTypeEnum.STOP_LIMIT.getCode(), tradeOrder.getOrderType());
    }

    /**
     * 是否是限价单
     *
     * @param tradeOrder
     * @return
     */
    public static boolean isLimitOrder(TradeOrder tradeOrder) {
        return StringUtils.equals(TradeOrderTypeEnum.LIMIT.getCode(), tradeOrder.getOrderType());
    }

    /**
     * 是否是机器人订单
     *
     * @param tradeOrder
     * @return
     */
    public static boolean isInterOrder(TradeOrder tradeOrder) {
        return StringUtils.equals(TradeOrderSrcEnum.INTER.getCode(), tradeOrder.getSrc());
    }

    /**
     * 是否是openapi订单
     *
     * @param tradeOrder
     * @return
     */
    public static boolean isOpenApiOrder(TradeOrder tradeOrder) {
        return StringUtils.equals(TradeOrderSrcEnum.OPENAPI.getCode(), tradeOrder.getSrc());
    }

    /**
     * 是否是用户来源下单
     *
     * @param tradeOrder
     * @return
     */
    public static boolean isUserSrcOrder(TradeOrder tradeOrder) {
        return !isInterOrder(tradeOrder) && !isOpenApiOrder(tradeOrder);
    }

    /**
     * 是否ETP 交易对
     *
     * @param pair
     * @return
     */
    public static boolean isEtp(String pair) {
        return pair.contains("3S") || pair.contains("3L") || pair.contains("5S") || pair.contains("5L");
    }

    /**
     * 获取取消订单缓存key
     *
     * @param tradeOrder
     * @return
     */
    public static String cancelOrderCacheKey(TradeOrder tradeOrder) {
        return ProcessOrderTypeEnum.CANCEL_ORDER.getCode() + ":" + tradeOrder.getId();
    }

    /**
     * 获取创建订单缓存key
     *
     * @param tradeOrder
     * @return
     */
    public static String createOrderCacheKey(TradeOrder tradeOrder) {
        return ProcessOrderTypeEnum.CREATE_ORDER.getCode() + ":" + tradeOrder.getId() + ":" + tradeOrder.getStatus();
    }

    /**
     * 判断成交额和成交量是否正确
     *
     * @param minTradeVolume
     * @param amount
     * @param price
     * @return
     */
    public static boolean canOrderMatch(BigDecimal minTradeVolume, BigDecimal amount, BigDecimal price) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        // 成交量太小
        /*if (Objects.nonNull(minTradeVolume)
                && minTradeAmt.compareTo(BigDecimal.ZERO) > 0
                && (amount.compareTo(minTradeAmt) < 0)) {
            return false;
        }*/
        // 成交额 太小
        if (Objects.nonNull(minTradeVolume)
                && minTradeVolume.compareTo(BigDecimal.ZERO) > 0
                && price.multiply(amount).compareTo(minTradeVolume) < 0) {
            return false;
        }
        return true;
    }

    /**
     * 获取kafka本地线程名称
     *
     * @return
     */
    public static String getBizThreadName() {
        String threadName = BIZ_THREAD_NAME.get();
        if (StringUtils.isEmpty(threadName)) {
            String[] names = Thread.currentThread().getName().split("#");
            if (names.length > 1) {
                threadName = names[1];
                BIZ_THREAD_NAME.set(threadName);
            }
        }
        return threadName;
    }

    /**
     * 拼接账户ID
     *
     * @param walletType
     * @param userId
     * @param coinType
     * @return
     */
    public static String buildAccountId(String walletType, String userId, String coinType) {
        String mainKey = mainKeyFromWalletTypeAndSubKey(walletType,
                subKeyForNonMargin(userId, coinType));
        return mainKey;
    }

    /**
     * 增加备注
     * @param
     * @return
     */
    public static void remarkBalanceChangeRequest(String traceId, BalanceChangeRequest balanceChangeRequest) {
        if(balanceChangeRequest == null || (balanceChangeRequest.getChangeRecords().isEmpty())
                || StringUtils.isEmpty(traceId)){
            return;
        }
        for (BalanceChangeRecord balanceChangeRecord : balanceChangeRequest.getChangeRecords()) {
            balanceChangeRecord.setRemark(Joiner.on(";").join(Lists.newArrayList(traceId, balanceChangeRecord.getRemark())));
        }
    }

    /**
     * 订单备注
     * @param tradeOrder
     * @return
     */
    public static String remarkForOrder(TradeOrder tradeOrder) {
        return TradeModelUtil.remarkForOrder(tradeOrder);
        //return Joiner.on(";").join(Lists.newArrayList(new String[]{getBizThreadName(), TradeModelUtil.remarkForOrder(tradeOrder)}));
    }

    /**
     * 是否创建计划委托
     *
     * @param tradeOrder
     * @return
     */
    public static boolean isCreateStopLimitOrder(TradeOrder tradeOrder) {
        return isStopLimitOrder(tradeOrder) && tradeOrder.getStatus() == TradeOrderStatusEnum.STOP_LIMIT_NOT_EFFECTIVE.getVal();
    }

    /**
     * 是否激活计划委托
     *
     * @param tradeOrder
     * @return
     */
    public static boolean isActiveStopLimitOrder(TradeOrder tradeOrder) {
        return isStopLimitOrder(tradeOrder) && tradeOrder.getStatus() == TradeOrderStatusEnum.NORMAL.getVal();
    }

    /**
     * 获取原子类
     *
     * @return
     */
    public static long atomicLong() {
        return ATOMIC_LONG.getAndIncrement();
    }

    /**
     * 转换kafka消息为cmd
     *
     * @param records
     * @return
     */
    public static List<TradeOrderCmd> convertKafkaMsgToTradeOrderCmd(List<ConsumerRecord<String, String>> records) {
        List<TradeOrderCmd> tradeOrderCmds = new ArrayList<>();
        for (ConsumerRecord<String, String> row : records) {
            TradeOrderCmd tradeOrderCmd = JSON.parseObject(row.value(), TradeOrderCmd.class);
            tradeOrderCmd.setPartition(row.partition());
            tradeOrderCmd.setOffset(row.offset());
            tradeOrderCmds.add(tradeOrderCmd);
        }
        return tradeOrderCmds;
    }

    /**
     * 过滤BKK和ETP币对
     *
     * @param pair
     * @return
     */
    public static boolean filterUselessPair(String pair) {
        if (StringUtils.equals("BKK_USDT", pair)) {
            return false;
        }
        String underline = "_";
        List<String> etpPairSuffixList = Arrays.asList("3S", "5S", "3L", "5L");
        try {
            if (pair.contains(underline)) {
                String[] arr = pair.split(underline);
                if (arr.length == 2) {
                    String coin = arr[0];
                    if (coin.length() > 2) {
                        String suffix = coin.substring(coin.length() - 2);
                        if (etpPairSuffixList.contains(suffix)) {
                            return false;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("filter useless pair err: {}", e.getMessage());
        }
        return true;
    }
}
