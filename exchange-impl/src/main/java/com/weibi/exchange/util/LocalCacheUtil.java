package com.weibi.exchange.util;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSON;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.weibi.config.api.CoinConfigService;
import com.weibi.config.api.PairBounsConfigService;
import com.weibi.config.api.SiteConfService;
import com.weibi.config.api.TradePairConfigService;
import com.weibi.config.constant.SiteConfigConstants;
import com.weibi.config.constant.TradeAppConstant;
import com.weibi.config.enums.CoinTypeEnum;
import com.weibi.config.enums.StopLimitOperatorEnum;
import com.weibi.config.enums.TradeOrderSrcEnum;
import com.weibi.config.model.CoinConfig;
import com.weibi.config.model.TradePair;
import com.weibi.config.model.TradePairConfig;
import com.weibi.config.model.trade.TradeOrder;
import com.weibi.config.model.trade.match.info.TradeFeeRateConfigInfo;
import com.weibi.core.balance.delega.model.UserCoinAccount;
import com.weibi.core.commons.logging.ILogging;
import com.weibi.core.commons.util.NumberUtil;
import com.weibi.exchange.core.ExchangeBusiness;
import com.weibi.exchange.core.TradePlate;
import com.weibi.exchange.model.Project;
import com.weibi.exchange.model.TradePairFrequency;
import com.weibi.exchange.model.User;
import com.weibi.exchange.service.ProjectService;
import com.weibi.exchange.service.TradeOrderUnfinishedService;
import com.weibi.exchange.service.TradePairFrequencyService;
import com.weibi.exchange.service.UserService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static com.alibaba.fastjson.JSON.parseArray;

/**
 * 本地缓存公共类
 *
 * @author jsong
 */
@Component
@RefreshScope
public class LocalCacheUtil implements ILogging {
    /***特殊平台用户redis key***/
    private static final String SPECIAL_PLATFORM_ACCOUNT = "finance:SPECIAL_PLATFORM_ACCOUNT_ID_LIST_KEY";
    /***用户未完成订单刷新db次数***/
    private static final int USER_UNFINISHED_ORDER_COUNT_REFRESH_DB_NUM = 5;
    /***用户余额刷新db次数***/
    private static final int USER_COIN_ACCOUNT_REFRESH_DB_NUM = 3;

    @Autowired
    private UserService userService;
    @Autowired
    private SiteConfService siteConfService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private TradePairConfigService tradePairConfigService;
    @Autowired
    private TradePairFrequencyService tradePairFrequencyService;
    @Resource
    private CoinConfigService coinConfigService;
    @Autowired
    private ProjectService projectService;
    @Autowired
    private PairBounsConfigService pairBounsConfigService;
    @Autowired
    private ExchangeBusiness exchangeBusiness;
    @Autowired
    private TradeOrderUnfinishedService tradeOrderUnfinishedService;

    /***平台用户缓存***/
    private final LoadingCache<String, List<String>> allPlatformUsers = Caffeine
            .newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build(new CacheLoader<String, List<String>>() {
                @Override
                public List<String> load(String key) {
                    try {
                        return userService.allPlatformUsers();
                    } catch (Exception e) {
                        error("加载用户缓存失败：", e);
                    }
                    return new ArrayList<>();
                }
            });
    /***不能使用bkk抵扣币种***/
    private final LoadingCache<String, List<String>> pairBounsConfigCache = Caffeine
            .newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build(new CacheLoader<String, List<String>>() {
                @Override
                public List<String> load(String key) {
                    try {
                        return pairBounsConfigService.getPairList();
                    } catch (Exception e) {
                        error("加载不能抵扣BKK抵扣配置缓存失败：", e);
                    }
                    return new ArrayList<>();
                }
            });
    /***订单ID缓存***/
    private final LoadingCache<String, Boolean> idempotentTradeOrderCache = Caffeine
            .newBuilder()
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .build(new CacheLoader<String, Boolean>() {
                @Override
                public Boolean load(String key) {
                    return false;
                }
            });
    /***交易手续费配置缓存***/
    private final LoadingCache<String, TradeFeeRateConfigInfo> tradeFeeRateConfigInfoCache = Caffeine
            .newBuilder()
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .build(new CacheLoader<String, TradeFeeRateConfigInfo>() {
                @Override
                public TradeFeeRateConfigInfo load(String key) {
                    try {
                        String val = siteConfService.getConf(SiteConfigConstants.KEY_TRADE_FEE_RATE_DISCOUNT, "");
                        if (StringUtils.isNotEmpty(val)) {
                            return JSON.parseObject(val, TradeFeeRateConfigInfo.class);
                        }
                    } catch (Exception e) {
                        error("加载币种手续费缓存失败:", e);
                    }
                    return new TradeFeeRateConfigInfo();
                }
            });
    /***最新成交价缓存***/
    private static final Cache<String, BigDecimal> pairPriceCache = Caffeine
            .newBuilder()
            .expireAfterAccess(1, TimeUnit.MINUTES)
            .build();
    /***交易对配置缓存***/
    private final LoadingCache<String, TradePairConfig> pairConfigByPairCache = Caffeine
            .newBuilder()
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .build(new CacheLoader<String, TradePairConfig>() {
                @Override
                public TradePairConfig load(String key) {
                    return tradePairConfigService.getPairConfigByPair(key);
                }
            });
    /***币种配置缓存***/
    private final LoadingCache<String, CoinConfig> coinConfigCache = Caffeine
            .newBuilder()
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .build(new CacheLoader<String, CoinConfig>() {
                @Override
                public CoinConfig load(String key) {
                    return coinConfigService.getCoinConfigByType(key);
                }
            });
    /***siteconf缓存***/
    private final LoadingCache<String, String> siteConfByConfNameCache = Caffeine
            .newBuilder()
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .build(new CacheLoader<String, String>() {
                @Override
                public String load(String key) {
                    return siteConfService.getConf(key);
                }
            });
    /***项目方缓存缓存***/
    private final LoadingCache<String, List<Project>> projectByUserIdCache = Caffeine
            .newBuilder()
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .build(new CacheLoader<String, List<Project>>() {
                @Override
                public List<Project> load(String userId) {
                    return projectService.getProjectInfo(userId, null, Boolean.TRUE, 1, 200);
                }
            });
    /***用户邀请人缓存***/
    private final LoadingCache<String, String> userInvitorByUserIdCache = Caffeine
            .newBuilder()
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .build(new CacheLoader<String, String>() {
                @Override
                public String load(String userId) {
                    String result = "NO_INVITOR";
                    try {
                        String key = Joiner.on(":").join(TradeAppConstant.APP_NAME_TRADE, "cache", "refer", userId);
                        String obj = stringRedisTemplate.opsForValue().get(key);
                        if (StringUtils.isNotEmpty(obj)) {
                            result = obj.toString();
                        } else {
                            User user = userService.findUserById(userId);
                            if (user != null) {
                                String inviteBy = user.getInviteBy();
                                result = org.apache.commons.lang3.StringUtils.isEmpty(inviteBy) ? "NO_INVITOR" : inviteBy;
                                stringRedisTemplate.opsForValue().set(key, result);
                                stringRedisTemplate.expire(key, 1, TimeUnit.HOURS);
                            }
                        }
                    } catch (Exception e) {
                        error("用户邀请人缓存失败：", e);
                    }
                    return result;
                }
            });
    /***平特特殊免手续费账户***/
    private final LoadingCache<String, Map<Object, Object>> specialPlatformAccountCache = Caffeine
            .newBuilder()
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .build(new CacheLoader<String, Map<Object, Object>>() {
                @Override
                public Map<Object, Object> load(String userId) {
                    Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(SPECIAL_PLATFORM_ACCOUNT);
                    return entries != null ? entries : new HashMap<>();
                }
            });
    /***币种等级配置表***/
    private final LoadingCache<String, Map<String, Integer>> tradePairFrequency = Caffeine
            .newBuilder()
            .expireAfterWrite(10, TimeUnit.SECONDS)
            .build(new CacheLoader<String, Map<String, Integer>>() {
                @Override
                public Map<String, Integer> load(String pair) {
                    Map<String, Integer> pairConfig = new HashMap<>();
                    try {
                        List<TradePairFrequency> tradePairFrequencyList = tradePairFrequencyService.getTradePairFrequencyList();
                        if (CollectionUtil.isNotEmpty(tradePairFrequencyList)) {
                            for (TradePairFrequency row : tradePairFrequencyList) {
                                pairConfig.put(row.getPair(), row.getPairLevel());
                            }
                        }
                        error("查询数据库等级配置：<{}>", JSON.toJSONString(tradePairFrequencyList));
                    } catch (Exception e) {
                        error("查询数据库等级配置失效：", e);
                    }
                    return pairConfig;
                }
            });
    /***币种等级配置表 拒绝访问币种***/
    private final LoadingCache<String, Boolean> tradePairFrequencyRefuse = Caffeine
            .newBuilder()
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .build(new CacheLoader<String, Boolean>() {
                @Override
                public Boolean load(String key) {
                    return false;
                }
            });
    /***平台手续费幂等缓存***/
    private final LoadingCache<String, Boolean> idempotentPublicFeeCache = Caffeine
            .newBuilder()
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .build(new CacheLoader<String, Boolean>() {
                @Override
                public Boolean load(String key) {
                    return false;
                }
            });
    /***平台手续费幂等缓存***/
    private final LoadingCache<String, Boolean> idempotentCancelOrderCache = Caffeine
            .newBuilder()
            .expireAfterWrite(2, TimeUnit.MINUTES)
            .build(new CacheLoader<String, Boolean>() {
                @Override
                public Boolean load(String key) {
                    return false;
                }
            });

    /***撮合内存盘口信息***/
    private final ConcurrentHashMap<String, TradePlate> tradePlateConcurrentHashMap = new ConcurrentHashMap<>();
    /***用戶余额缓存***/
    private ConcurrentHashMap<String, UserCoinAccount> userCoinAccountHashMap = new ConcurrentHashMap<>();
    /***用戶余额缓存 间隔多少次刷新缓存***/
    private ConcurrentHashMap<String, AtomicInteger> userCoinAccountGetCountHashMap = new ConcurrentHashMap<>();
    /***用戶下单数量检测***/
    private ConcurrentHashMap<String, AtomicLong> userUnfinishedOrderCountHashMap = new ConcurrentHashMap<>();
    /***用戶下单数据检测获取 间隔多少次刷新缓存***/
    private ConcurrentHashMap<String, AtomicInteger> userUnfinishedOrderGetCountHashMap = new ConcurrentHashMap<>();

    /**
     * 获取平台用户
     *
     * @return
     */
    public List<String> getPlatFormUser() {
        return allPlatformUsers.get("*");
    }

    /**
     * 获取不能抵扣bkk币种配置
     *
     * @return
     */
    public List<String> getPairBounsConfig() {
        return pairBounsConfigCache.get("*");
    }

    /**
     * 订单幂等判断
     *
     * @param key
     * @param isAdd
     * @return
     */
    public boolean tradeOrderIsProc(String key, boolean isAdd) {
        Boolean bool = idempotentTradeOrderCache.getIfPresent(key);
        if (bool == null && isAdd) {
            idempotentTradeOrderCache.put(key, true);
        }
        return bool != null ? true : false;
    }

    /**
     * 获取交易手续费配置
     *
     * @return
     */
    public TradeFeeRateConfigInfo getTradeFeeRateConfigInfo() {
        return tradeFeeRateConfigInfoCache.get("*");
    }

    /**
     * 更新本地缓存
     *
     * @param pair
     * @param price
     */
    public void updatePairPrice(String pair, BigDecimal price) {
        pairPriceCache.put(pair, price);
    }

    /**
     * 获取bkk交个
     *
     * @param coin
     * @param knownPair
     * @param knownPrice
     * @return
     */
    public BigDecimal getCoinBkkPrice(String coin, String knownPair, BigDecimal knownPrice) {
        if (StringUtils.equals(coin, CoinTypeEnum.BKK.getCode())) {
            return BigDecimal.ONE;
        }

        BigDecimal coinUsdtPrice = getCurrentUsdtPriceForCoin(coin, knownPair, knownPrice);
        String bkkUsdtPair = new TradePair(CoinTypeEnum.BKK.getCode(), CoinTypeEnum.USDT.getCode()).toString();
        BigDecimal bkkUsdtPrice = getCurrentPairPrice(bkkUsdtPair);

        if (NumberUtil.emptyOrZero(coinUsdtPrice) || NumberUtil.emptyOrZero(bkkUsdtPrice)) {
            return null;
        }

        return coinUsdtPrice.divide(bkkUsdtPrice, 18, RoundingMode.DOWN);
    }

    /**
     * 获取当前币种对usdt价格
     *
     * @param coin
     * @param knownPair
     * @param knownPrice
     * @return
     */
    private BigDecimal getCurrentUsdtPriceForCoin(String coin, String knownPair, BigDecimal knownPrice) {
        //1. 如果是USDT，则直接返回
        if (org.apache.commons.lang3.StringUtils.equalsIgnoreCase(CoinTypeEnum.USDT.getCode(), coin)) {
            return BigDecimal.ONE;
        }
        TradePair pairInUsdtArea = new TradePair(coin, CoinTypeEnum.USDT.getCode());
        if (StringUtils.equals(knownPair, pairInUsdtArea.toString())) {
            return knownPrice;
        }

        //2. 尝试寻找USDT交易对，如果有，则是对应的价格
        BigDecimal targetCoinInUsdtAreaPrice = getCurrentPairPrice(pairInUsdtArea.toString());
        if (!NumberUtil.emptyOrZero(targetCoinInUsdtAreaPrice)) {
            return targetCoinInUsdtAreaPrice;
        }

        //3. 找到对应交易区对应的价格，如果没有则返回零
        String anchorCoin = new TradePair(knownPair).getAnchoredCoin();
        TradePair anchorCoinUsdtPair = new TradePair(anchorCoin, CoinTypeEnum.USDT.getCode());
        BigDecimal anchorCoinUsdtPrice = getCurrentPairPrice(anchorCoinUsdtPair.toString());
        if (!NumberUtil.emptyOrZero(anchorCoinUsdtPrice)) {
            return knownPrice.multiply(anchorCoinUsdtPrice);
        }

        return BigDecimal.ZERO;
    }

    /**
     * 获取币种价格
     *
     * @param pair
     * @return
     */
    private BigDecimal getCurrentPairPrice(String pair) {
        BigDecimal price = pairPriceCache.getIfPresent(pair);
        if (price != null && !NumberUtil.emptyOrZero(price)) {
            return price;
        }

        String val = getRedisPairCurrentPrice(pair);
        if (StringUtils.isEmpty(val)) {
            return BigDecimal.ZERO;
        }
        price = new BigDecimal(val);
        pairPriceCache.put(pair, price);
        return price;
    }

    /**
     * 获取redis币种价格
     *
     * @param pair
     * @return
     */
    private String getRedisPairCurrentPrice(String pair) {
        return stringRedisTemplate.opsForValue().get(keyForPairCurrentPrice(pair));
    }

    /**
     * 构建redis币种价格key
     *
     * @param pair
     * @return
     */
    private String keyForPairCurrentPrice(String pair) {
        return "trade:stats:current_price:" + pair;
    }

    /**
     * 计划委托下单成功更新redis缓存
     *
     * @param tradeOrder
     */
    public void createLimitOrderCache(TradeOrder tradeOrder) {
        if (StringUtils.equals(tradeOrder.getOperator(), StopLimitOperatorEnum.LTE.getCode())) {
            stringRedisTemplate.opsForZSet().add(keyForPriceRange(tradeOrder.getPair(), true), JSON.toJSONString(tradeOrder), tradeOrder.getStopPrice().doubleValue());
        } else {
            stringRedisTemplate.opsForZSet().add(keyForPriceRange(tradeOrder.getPair(), false), JSON.toJSONString(tradeOrder), tradeOrder.getStopPrice().doubleValue());
        }
        stringRedisTemplate.opsForValue().increment(keyForOrderCount(tradeOrder.getUserId(), tradeOrder.getPair()), 1);
    }

    /**
     * 根据币对和价格查找计划委托订单
     *
     * @param pair
     * @param price
     * @return
     */
    public List<TradeOrder> findByStopLimitPairPriceRange(String pair, BigDecimal price) {
        List<TradeOrder> result = new ArrayList<>();

        Set set = stringRedisTemplate.opsForZSet().rangeByScore(keyForPriceRange(pair, true), price.doubleValue(), Integer.MAX_VALUE);
        for (Object str : set) {
            result.add(JSON.parseObject(str.toString(), TradeOrder.class));
        }

        Set set2 = stringRedisTemplate.opsForZSet().rangeByScore(keyForPriceRange(pair, false), 0, price.doubleValue());
        for (Object str2 : set2) {
            result.add(JSON.parseObject(str2.toString(), TradeOrder.class));
        }

        return result;
    }

    /**
     * 从redis中删除计划委托订单
     *
     * @param tradeOrder
     */
    public void deleteStopLimitCache(TradeOrder tradeOrder) {
        deleteStopLimitCache(tradeOrder.getId(), tradeOrder.getPair(), tradeOrder.getOperator(), tradeOrder.getStopPrice());
    }

    /**
     * 从redis中删除计划委托订单
     *
     * @param orderId
     * @param pair
     * @param oper
     * @param stopPrice
     */
    private void deleteStopLimitCache(String orderId, String pair, String oper, BigDecimal stopPrice) {
        boolean lessThan = StringUtils.equals(oper, StopLimitOperatorEnum.LTE.getCode());

        Set set = stringRedisTemplate.opsForZSet().rangeByScore(keyForPriceRange(pair, lessThan), stopPrice.doubleValue()
                , stopPrice.doubleValue());

        set.forEach(new Consumer() {
            @Override
            public void accept(Object o) {
                TradeOrder orderInCache = JSON.parseObject(o.toString(), TradeOrder.class);

                if (StringUtils.equals(orderInCache.getId(), orderId)) {
                    long result = stringRedisTemplate.opsForZSet().remove(keyForPriceRange(pair, lessThan), orderInCache.toString());
                    warn("成功删除 <{}><{}><{}><{}> 计划委托缓存,结果成功? <{}>", orderId, pair, oper, stopPrice, result == 1);
                    if (result == 1) {
                        stringRedisTemplate.opsForValue().increment(keyForOrderCount(orderInCache.getUserId(), orderInCache.getPair()), -1);
                    }

                }
            }
        });
    }

    /**
     * 构建计划委托rediskey
     *
     * @param pair
     * @param lessThan
     * @return
     */
    private String keyForPriceRange(String pair, boolean lessThan) {
        if (lessThan) {
            return TradeAppConstant.APP_NAME_TRADE + ":stop_limit:" + pair + ":lte";
        } else {
            return TradeAppConstant.APP_NAME_TRADE + ":stop_limit:" + pair + ":gte";
        }
    }

    /**
     * 构建计划委托rediskey
     *
     * @param userId
     * @param pair
     * @return
     */
    private String keyForOrderCount(String userId, String pair) {
        return StringUtils.join(Lists.newArrayList(TradeAppConstant.APP_NAME_TRADE, "stop_limit", userId, pair), ":");
    }

    /**
     * 构建计划委托rediskey
     *
     * @param userId
     * @return
     */
    private String keyPatternForOrderCount(String userId) {
        return StringUtils.join(Lists.newArrayList(TradeAppConstant.APP_NAME_TRADE, "stop_limit", userId, "*"), ":");
    }

    /**
     * 交易对配置缓存
     *
     * @param pair
     * @return
     */
    public TradePairConfig getPairConfigByPair(String pair) {
        return pairConfigByPairCache.get(pair);
    }

    /**
     * siteconf缓存
     *
     * @param confName
     * @param defVal
     * @return
     */
    public String getSiteConfByKey(String confName, String defVal) {
        String value = siteConfByConfNameCache.get(confName);
        return StringUtils.isNotEmpty(value) ? value : defVal;
    }

    /**
     * 获取项目方用户
     *
     * @param userId
     * @return
     */
    public List<Project> getProjectByUserId(String userId) {
        return projectByUserIdCache.get(userId);
    }

    /**
     * 获取用户邀请人
     *
     * @param userId
     * @return
     */
    public String getUserInvitorByUserId(String userId) {
        return userInvitorByUserIdCache.get(userId);
    }

    /**
     * 获取币种配置缓存
     *
     * @param coinType
     * @return
     */
    public CoinConfig getCoinConfigByType(String coinType) {
        return coinConfigCache.get(coinType);
    }

    /**
     * 获取平台特殊免手续费用户
     *
     * @param userId
     * @return
     */
    public boolean getSpecialPlatformAccountCache(String userId) {
        return specialPlatformAccountCache.get(userId).containsKey(userId);
    }

    /**
     * 获取币种交易频率币对
     *
     * @param pair
     * @return
     */
    public Integer getExchangePairLevel(String pair) {
        Map<String, Integer> pairConfig = tradePairFrequency.get("*");
        return pairConfig.get(pair);
    }

    /**
     * 获取币对是否 暂停交易
     *
     * @param pair
     * @return
     */
    public boolean getExchangePairRefuse(String pair) {
        return tradePairFrequencyRefuse.get(pair);
    }

    /**
     * 设置币种是否暂替交易
     *
     * @param pair
     * @param flag
     */
    public void setExchangePairRefuse(String pair, boolean flag) {
        tradePairFrequencyRefuse.put(pair, flag);
    }

    /**
     * 订单幂等判断
     *
     * @param partition
     * @param isAdd
     * @return
     */
    public boolean publicFeeIsProc(int partition, long offset, boolean isAdd) {
        String key = partition + ":" + offset;
        Boolean bool = idempotentPublicFeeCache.getIfPresent(key);
        if (bool == null && isAdd) {
            idempotentPublicFeeCache.put(key, true);
        }
        return bool != null ? true : false;
    }

    /**
     * 是否重复撤单
     *
     * @param key
     * @param isAdd
     * @return
     */
    public boolean apiCancelOrderIsProc(String key, boolean isAdd) {
        Boolean bool = idempotentCancelOrderCache.getIfPresent(key);
        if (bool == null && isAdd) {
            idempotentCancelOrderCache.put(key, true);
        }
        return bool != null ? true : false;
    }


    /**
     * 获取需要记录账单的用户
     *
     * @return
     */
    public List<String> getUsersNeedTradeInfo() {
        try {
            String users = getSiteConfByKey("USERS_NEED_BILLS", JSON.toJSONString(Lists.newArrayList()));
            return parseArray(users, String.class);
        } catch (Exception e) {
            error("获取配USERS_NEED_BILLS置失败:", e);
        }
        return new ArrayList<>();
    }

    /**
     * 获取本地缓存统计信息
     *
     * @return
     */
    public Map<String, Object> getLocalCacheStatistics() {
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("allPlatformUsers", allPlatformUsers.get("*").size());
        resultMap.put("idempotentTradeOrderCache", idempotentTradeOrderCache.estimatedSize());
        resultMap.put("tradeFeeRateConfigInfoCache", tradeFeeRateConfigInfoCache.estimatedSize());
        resultMap.put("pairPriceCache", pairPriceCache.estimatedSize());
        resultMap.put("pairConfigByPairCache", pairConfigByPairCache.estimatedSize());
        resultMap.put("coinConfigCache", coinConfigCache.estimatedSize());
        resultMap.put("siteConfByConfNameCache", siteConfByConfNameCache.estimatedSize());
        resultMap.put("projectByUserIdCache", projectByUserIdCache.estimatedSize());
        resultMap.put("userInvitorByUserIdCache", userInvitorByUserIdCache.estimatedSize());
        resultMap.put("specialPlatformAccountCache", specialPlatformAccountCache.estimatedSize());
        resultMap.put("tradePairFrequency", tradePairFrequency.estimatedSize());
        resultMap.put("tradePairFrequencyRefuse", tradePairFrequencyRefuse.estimatedSize());
        resultMap.put("idempotentPublicFeeCache", idempotentPublicFeeCache.estimatedSize());
        return resultMap;
    }

    /**
     * 获取内存盘口信息
     *
     * @param pair
     * @return
     */
    public TradePlate getTradePlate(String pair) {
        return tradePlateConcurrentHashMap.get(pair);
    }

    /**
     * 更新内存盘口信息
     *
     * @param pair
     * @param tradePlate
     * @return
     */
    public TradePlate setTradePlate(String pair, TradePlate tradePlate) {
        return tradePlateConcurrentHashMap.put(pair, tradePlate);
    }

    /**
     * 获取用户余额， 如果是机器人的抽样检查，如果是用户订单每次实时检查
     *
     * @param src
     * @param accountId
     * @return
     */
    public boolean checkUserCoinAccountForCache(String src, String accountId, BigDecimal cost) {
        if (!StringUtils.equals(TradeOrderSrcEnum.INTER.getCode(), src)) {
            UserCoinAccount userCoinAccount = exchangeBusiness.selectExAccountNoLockInMaster(accountId);
            return userCoinAccount == null ? false : userCoinAccount.getCoinAmount().compareTo(cost) >= 0;
        } else {
            UserCoinAccount userCoinAccount = userCoinAccountHashMap.get(accountId);
            if (userCoinAccount == null) {
                userCoinAccount = exchangeBusiness.selectExAccountNoLockInMaster(accountId);
                userCoinAccountHashMap.put(accountId, userCoinAccount);
                return userCoinAccount == null ? false : userCoinAccount.getCoinAmount().compareTo(cost) >= 0;
            } else {
                //每次X次刷新一次数据库
                AtomicInteger atomicInteger = userCoinAccountGetCountHashMap.get(accountId);
                if (atomicInteger == null) {
                    atomicInteger = new AtomicInteger(USER_COIN_ACCOUNT_REFRESH_DB_NUM);
                    userCoinAccountGetCountHashMap.put(accountId, atomicInteger);
                }
                synchronized (userCoinAccount) {
                    if (atomicInteger.getAndDecrement() <= 0) {
                        // 缓存不存在 实时查询覆盖
                        UserCoinAccount coinAccount = exchangeBusiness.selectExAccountNoLockInMaster(accountId);
                        userCoinAccount.setCoinAmount(coinAccount != null ? coinAccount.getCoinAmount() : BigDecimal.ZERO);
                        userCoinAccount.setCoinFroze(coinAccount != null ? coinAccount.getCoinFroze() : BigDecimal.ZERO);
                        atomicInteger.set(USER_COIN_ACCOUNT_REFRESH_DB_NUM);
                    }

                    return userCoinAccount == null ? false : userCoinAccount.getCoinAmount().compareTo(cost) >= 0;
                }
            }
        }
    }

    /**
     * 检查用户下单数量 用户请求实施查询 机器人抽样查询
     *
     * @param src
     * @param userId
     * @param pair
     * @param direction
     * @param limit
     * @return
     */
    public boolean checkUserUnfinishedOrderCount(String src, String userId, String pair, String direction, long limit) {
        if (!StringUtils.equals(TradeOrderSrcEnum.INTER.getCode(), src)) {
            // 非机器人实时查询
            long unfinishedOrderCount = tradeOrderUnfinishedService.countExUnfinishedOrderByIdAndPair(userId, pair, direction);
            return unfinishedOrderCount < limit;
        } else {
            // 机器人查询缓存
            String key = Joiner.on("_").join(pair, direction, userId);
            AtomicLong count = userUnfinishedOrderCountHashMap.get(key);
            if (count == null) {
                // 缓存不存在 实时查询覆盖
                long unfinishedOrderCount = tradeOrderUnfinishedService.countExUnfinishedOrderByIdAndPair(userId, pair, direction);
                count = new AtomicLong(unfinishedOrderCount);
                userUnfinishedOrderCountHashMap.put(key, count);
                return unfinishedOrderCount < limit;
            } else {
                //每次X次刷新一次数据库
                AtomicInteger atomicInteger = userUnfinishedOrderGetCountHashMap.get(key);
                if (atomicInteger == null) {
                    atomicInteger = new AtomicInteger(USER_UNFINISHED_ORDER_COUNT_REFRESH_DB_NUM);
                    userUnfinishedOrderGetCountHashMap.put(key, atomicInteger);
                }
                if (atomicInteger.getAndDecrement() <= 0) {
                    // 缓存不存在 实时查询覆盖
                    long unfinishedOrderCount = tradeOrderUnfinishedService.countExUnfinishedOrderByIdAndPair(userId, pair, direction);
                    count.set(unfinishedOrderCount);
                    atomicInteger.set(USER_UNFINISHED_ORDER_COUNT_REFRESH_DB_NUM);
                    return unfinishedOrderCount < limit;
                }
                return count.getAndIncrement() < limit;
            }
        }
    }

    /**
     * 撤单更新缓存订单
     *
     * @param userId
     * @param pair
     * @param direction
     */
    public void cancelOrderUpdataOrderCount(String userId, String pair, String direction) {
        String key = Joiner.on("_").join(pair, direction, userId);
        AtomicLong count = userUnfinishedOrderCountHashMap.get(key);
        if (count != null) {
            count.getAndDecrement();
        }
    }



}
