package com.weibi.exchange.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.convert.Convert;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.weibi.config.api.TradePriceService;
import com.weibi.config.constant.ApiConstant;
import com.weibi.config.constant.SiteConfigConstants;
import com.weibi.config.constant.TradeAppConstant;
import com.weibi.config.enums.*;
import com.weibi.config.model.CoinConfig;
import com.weibi.config.model.TradePair;
import com.weibi.config.model.TradePairConfig;
import com.weibi.config.model.trade.TradeDeal;
import com.weibi.config.model.trade.TradeOrder;
import com.weibi.config.model.trade.match.info.ExchangeAmountInfo;
import com.weibi.config.model.trade.match.info.LevelFeeRateInfo;
import com.weibi.config.util.TradeModelUtil;
import com.weibi.core.balance.delega.enums.UserWalletTypeEnum;
import com.weibi.core.balance.delega.model.BalanceChangeRequest;
import com.weibi.core.balance.delega.model.UserCoinAccount;
import com.weibi.core.commons.api.ApiResponse;
import com.weibi.core.commons.api.BaseService;
import com.weibi.core.commons.db.extension.service.impl.BaseServiceImpl;
import com.weibi.core.commons.logging.ILogging;
import com.weibi.core.commons.model.BizTypeConstants;
import com.weibi.core.commons.model.mybatis.BasePage;
import com.weibi.core.commons.util.CommonResponseUtil;
import com.weibi.core.commons.util.IdGenerator;
import com.weibi.core.commons.util.NumberUtil;
import com.weibi.core.commons.util.RandomUtil;
import com.weibi.core.language.util.LanguageUtils;
import com.weibi.exchange.api.ExchangeService;
import com.weibi.exchange.core.ExchangeBusiness;
import com.weibi.exchange.core.ProcessOrderTypeEnum;
import com.weibi.exchange.core.TradeOrderCmd;
import com.weibi.exchange.core.TradePlate;
import com.weibi.exchange.entity.TestEntity;
import com.weibi.exchange.model.*;
import com.weibi.exchange.service.TradeOrderUnfinishedService;
import com.weibi.exchange.service.UserProfileService;
import com.weibi.exchange.util.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.apache.shardingsphere.api.hint.HintManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

import static com.weibi.config.constant.ApiConstant.CODE_ORDER_NOT_EXISTS;
import static com.weibi.config.constant.UserConstants.BEIYONG_4;
import static com.weibi.config.enums.TradeFeeConstants.OPEN_BKK_WITHHOLD;
import static com.weibi.config.enums.TradeOrderStatusEnum.STOP_LIMIT_NOT_EFFECTIVE;
import static com.weibi.core.commons.api.CommonRespConstant.INVALID_PARAM_CODE;
import static com.weibi.core.commons.api.CommonRespConstant.RESULT_DUPLICATE;
import static com.weibi.core.commons.model.BizTypeConstants.JOINER_BIZ_TYPE;
import static com.weibi.exchange.util.MetricsUtil.PROCESS_API_CANCEL_ORDER_ERROR_TPS_RT;
import static com.weibi.exchange.util.MetricsUtil.PROCESS_API_CREATE_ORDER_ERROR_TPS_RT;


/**
 * @author jsong
 * @version 1.0
 * @date 2020-06-13 19:08
 */
@Service
@Primary
public class ExchangeServiceImpl extends BaseServiceImpl<TestEntity> implements ExchangeService, BaseService, ILogging {

    /***支持交易订单类型***/
    private static final List<String> POSSIBLE_TRADE_TYPES =
            ImmutableList.of(TradeOrderTypeEnum.STOP_LIMIT.getCode(), TradeOrderTypeEnum.LIMIT.getCode(), TradeOrderTypeEnum.MARKET.getCode());
    /***支持交易 钱包类型***/
    private static final List<String> POSSIBLE_WALLET_TYPES_CREATE_TRADE =
            ImmutableList.of(UserWalletTypeEnum.WALLET.getCode(), UserWalletTypeEnum.MARGIN.getCode());
    private static final String PAIR_FREE_FEE_USER_ID_LIST_KEY_PREFIX = "PAIR:FREE_FEE_USER_ID_LIST_";
    private final static BigDecimal TRADE_ORDER_PRICE_MAX_DECIMAL = new BigDecimal("10000000");
    private static final String ORDER_LIMIT_PER_USER_KEY = BizTypeConstants.TRADE + JOINER_BIZ_TYPE + "ORDER_LIMIT_PER_USER_KEY";
    private static final String KEY_TRADE_RATE_LIMIT_WHITE_LIST = "trade:limit_user_white_list";
    private static final String KEY_TRADE_RATE_LIIMIT = "trade:cmd_limit_per_sec";
    private static final String KEY_TRADE_MACHINE_RATE_LIIMIT_TIMES = "trade:machine_rate_times";
    /***系统接用户大单配置缓存key***/
    private static final String AUTO_TRADE_ORDER_KEY = "AUTO_TRADE_ORDER_";
    /***用户挂大单与系统接单挂大单关系缓存key***/
    private static final String AUTO_TRADE_ORDER_RELATION_KEY = "AUTO_TRADE_ORDER_RELATION_";
    /***大单吃单配置最大有效时间***/
    private static final long EFFECTIVE_TIME_RANGE = 360000;
    /***最大持续挂单次数***/
    private static final int MAX_CONTINUE_ORDER = 20;
    /***最大下单次数***/
    private static final int MAX_ORDER_NUMBER = 10;

    @Autowired
    private TradePriceService tradePriceService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private TradeOrderUnfinishedService tradeOrderUnfinishedService;
    @Autowired
    private UserProfileService userProfileService;
    @Autowired
    private ExchangeKafkaUtil exchangeKafkaUtil;
    @Autowired
    private LocalCacheUtil localCacheUtil;
    @Autowired
    private MetricsUtil metricsUtil;
    @Autowired
    private RateLimiterUtil rateLimiterUtil;
    @Autowired
    private ExchangeBusiness exchangeBusiness;

    @Override
    public ApiResponse createOrderForUser(String userId, String pair, String direction, BigDecimal amount, String walletType, String orderType, BigDecimal price, BigDecimal stopPrice, String operator, String src) {
        return createOrderWrapper(userId, pair, direction, amount, walletType, orderType, price, stopPrice, operator, src);
    }

    @Override
    public ApiResponse createOrderForMachine(String userId, String pair, String direction, BigDecimal amount, String walletType, String orderType, BigDecimal price) {
        return createOrderWrapper(userId, pair, direction, amount, walletType, orderType, price, null, null, TradeOrderSrcEnum.INTER.getCode());
    }

    @Override
    public ApiResponse createOrderForOpenApi(String userId, String pair, String direction, BigDecimal amount, String walletType, String orderType, BigDecimal price, BigDecimal stopPrice, String operator) {
        return createOrderWrapper(userId, pair, direction, amount, walletType, orderType, price, stopPrice, operator, TradeOrderSrcEnum.OPENAPI.getCode());
    }

    @Override
    public ApiResponse cancelOrderForUser(String userId, String orderNo, String pair) {
        return cancelOrderWrapper(userId, orderNo, TradeOrderSrcEnum.NORMAL_MOBILE.getCode());
    }

    @Override
    public ApiResponse cancelOrderForMachine(String userId, String orderNo, String pair) {
        return cancelOrderWrapper(userId, orderNo, TradeOrderSrcEnum.INTER.getCode());
    }

    @Override
    public ApiResponse cancelOrderForOpenApi(String userId, String orderNo, String pair) {
        return cancelOrderWrapper(userId, orderNo, TradeOrderSrcEnum.OPENAPI.getCode());
    }

    @Override
    public ApiResponse activeStopLimitOrder(TradeOrder tradeOrder) {
        TradeOrder tradeOrderDb = tradeOrderUnfinishedService.findOrderById(tradeOrder.getId());
        if (tradeOrderDb == null) {
            localCacheUtil.deleteStopLimitCache(tradeOrder);
            return ApiResponse.failResult();
        }
        if (tradeOrder.getStatus() != TradeOrderStatusEnum.STOP_LIMIT_NOT_EFFECTIVE.getVal()) {
            localCacheUtil.deleteStopLimitCache(tradeOrder);
            return ApiResponse.failResult();
        }

        //检查余额
        ExchangeAmountInfo exchangeInfo = TradeModelUtil.toExchangeInfo(new TradePair(tradeOrder.getPair()), tradeOrder.getDirection(),
                tradeOrder.getTotalAmount(), tradeOrder.getPrice());
        // 生效设置冻结值
        tradeOrder.setFrozenAmountByOrder(exchangeInfo.getReduceCoinAmount());
        tradeOrder.setStatus(TradeOrderStatusEnum.NORMAL.getVal());
        BigDecimal cost = exchangeInfo.getReduceCoinAmount();
        String reduceCoinType = exchangeInfo.getReduceCoinType();
        UserCoinAccount userCoinAccount = exchangeBusiness.selectExAccountNoLockInMaster(
                BizUtil.buildAccountId(tradeOrder.getWalletType(), tradeOrder.getUserId(), reduceCoinType));
        boolean isEnough = userCoinAccount == null ? false : userCoinAccount.getCoinAmount().compareTo(cost) >= 0;
        if (!isEnough) {
            CoinConfig coinConfig = localCacheUtil.getCoinConfigByType(reduceCoinType);
            BigDecimal minTradeAmt = coinConfig.getMinimumTradeAmount();
            if (userCoinAccount.getCoinAmount().compareTo(minTradeAmt) >= 0) {
                TradePairConfig tradePairConfig = localCacheUtil.getPairConfigByPair(tradeOrder.getPair());
                int amountPrecision = tradePairConfig.getAmountPrecision();
                BigDecimal canDealAmount;
                if (TradeDirectionEnum.isBid(tradeOrder.getDirection())) {
                    canDealAmount = userCoinAccount.getCoinAmount().divide(tradeOrder.getPrice(), amountPrecision, RoundingMode.DOWN);
                    tradeOrder.setFrozenAmountByOrder(canDealAmount.multiply(tradeOrder.getPrice()));
                } else {
                    canDealAmount = NumberUtil.getFormattedDecimal(userCoinAccount.getCoinAmount(), amountPrecision, RoundingMode.DOWN);
                    tradeOrder.setFrozenAmountByOrder(canDealAmount);
                }
                tradeOrder.setTotalAmount(canDealAmount);
            }
        }
        TradeOrderCmd tradeOrderCmd = new TradeOrderCmd(tradeOrder, ProcessOrderTypeEnum.CREATE_ORDER.getCode());
        boolean result = exchangeKafkaUtil.sendMsgForApiExchangeOrder(tradeOrderCmd);
        if (result) {
            localCacheUtil.deleteStopLimitCache(tradeOrder);
            return ApiResponse.succResult();
        } else {
            return ApiResponse.failResult();
        }
    }

    @Override
    public ApiResponse activeStopLimitDeal(String pair, BigDecimal amount, String direction, BigDecimal price) {
        TradeDeal tradeDeal = new TradeDeal();
        String dealId = IdGenerator.generateKey("2018011114202087319000014").toString();
        tradeDeal.setId(dealId);
        tradeDeal.setBuyerId("2018011114202087319000014");
        tradeDeal.setSellerId("2018011114202087319000014");
        tradeDeal.setPrice(price);
        tradeDeal.setDealAmount(amount);
        tradeDeal.setPair(pair);
        tradeDeal.setDealDirection(direction);
        //tradeDeal.setBuyerFeeCoinType(bidExchangeInfo.getRealFeeCoinType());
        //tradeDeal.setBuyerFee(bidExchangeInfo.getRealFeeAmt());
        //tradeDeal.setBuyerSrc(bidOrder.getSrc());
        tradeDeal.setRefBuyOrder("NON_EXIST");
        //tradeDeal.setBuyerReferredBy(bidOrder.getReferredBy());
        //tradeDeal.setSellerFeeCoinType(askExchangeInfo.getRealFeeCoinType());
        //tradeDeal.setSellerFee(askExchangeInfo.getRealFeeAmt());
        tradeDeal.setSellerSrc("");
        tradeDeal.setRefSellOrder("NON_EXIST");
        tradeDeal.setSellerReferredBy("");
        tradeDeal.setCreatedTime(new Date());
        //exchangeKafkaUtil.sendDealMade(tradeDeal);
        return ApiResponse.succResult();
    }

    @Override
    public ApiResponse<OrdersDepthVo> getQuotationOrdersDepth(String pair, Integer depth, int amountPrecision, int pricePrecision) {
        TradePlate tradePlate = localCacheUtil.getTradePlate(pair);
        if (tradePlate == null) {
            return ApiResponse.failResult();
        }
        TreeMap<BigDecimal, BigDecimal> buyPlateQueue = tradePlate.getBuyPlateQueue();
        OrdersDepthVo ordersDepthVo = new OrdersDepthVo();
        depth = depth != null ? depth : 20;
        if (buyPlateQueue != null && buyPlateQueue.size() > 0) {
            for (BigDecimal k : buyPlateQueue.keySet()) {
                OrdersDepthItemVo ordersDepthItemVo = new OrdersDepthItemVo();
                ordersDepthItemVo.setPrice(k.setScale(pricePrecision, RoundingMode.DOWN));
                ordersDepthItemVo.setExchangeAmount(buyPlateQueue.get(k).setScale(amountPrecision, RoundingMode.DOWN));
                ordersDepthVo.getBidDepth().add(ordersDepthItemVo);
                if (ordersDepthVo.getBidDepth().size() == depth) {
                    break;
                }
            }
        }
        TreeMap<BigDecimal, BigDecimal> sellPlateQueue = tradePlate.getSellPlateQueue();
        if (sellPlateQueue != null && sellPlateQueue.size() > 0) {
            for (BigDecimal k : sellPlateQueue.keySet()) {
                OrdersDepthItemVo ordersDepthItemVo = new OrdersDepthItemVo();
                ordersDepthItemVo.setPrice(k.setScale(pricePrecision, RoundingMode.DOWN));
                ordersDepthItemVo.setExchangeAmount(sellPlateQueue.get(k).setScale(amountPrecision, RoundingMode.DOWN));
                ordersDepthVo.getAskDepth().add(ordersDepthItemVo);
                if (ordersDepthVo.getAskDepth().size() == depth) {
                    break;
                }
            }
        }
        return ApiResponse.succResult().setData(ordersDepthVo);
    }

    @Override
    public BasePage<TradeOrderUnfinished> getTradeOrderUnfinishedList(String id, String userId, String pair, String direction, String src, String orderType, Integer status, Integer page, Integer pageSize) {
        return tradeOrderUnfinishedService.getTradeOrderUnfinishedList(id, userId, pair, direction, src, orderType, status, page, pageSize);
    }

    @Override
    public ApiResponse<List<String>> getPairForTradeOrderUnfinished(String userId) {
        List<String> prirList = tradeOrderUnfinishedService.getPairForTradeOrderUnfinished(userId);
        return ApiResponse.succResult().setData(prirList);
    }

    @Override
    public ApiResponse<List<BatchCancelOrderResult>> batchCancelOrder(BatchCancelParamDTO batchCancelParamDTO) {
        if (CollectionUtil.isEmpty(batchCancelParamDTO.getIds())
                && StringUtils.isEmpty(batchCancelParamDTO.getUserId())
                && StringUtils.isEmpty(batchCancelParamDTO.getPair())) {
            return ApiResponse.failResult().setCode(65);
        }
        List<BatchCancelOrderResult> resultList = new ArrayList<>();
        List<TradeOrderUnfinished> tradeOrderUnfinisheds = tradeOrderUnfinishedService.getTradeOrderUnfinishedByIdsAndUserIdAndPair(
                batchCancelParamDTO.getIds(), batchCancelParamDTO.getUserId(), batchCancelParamDTO.getPair());
        if (CollectionUtil.isNotEmpty(tradeOrderUnfinisheds)) {
            tradeOrderUnfinisheds.forEach(item -> {
                ApiResponse response = cancelOrder(Convert.convert(TradeOrder.class, item));
                resultList.add(new BatchCancelOrderResult(item.getId(), response.successful() ? 0 : 1, ""));
            });
        }
        return ApiResponse.succResult().setData(resultList);
    }

    private ApiResponse createOrderWrapper(String userId,
                                           String pair,
                                           String direction,
                                           BigDecimal amount,
                                           String walletType,
                                           String orderType,
                                           BigDecimal price,
                                           BigDecimal stopPrice,
                                           String operator,
                                           String src) {
        ApiResponse apiResponse = metricsUtil.timerMetricByRes(MetricsUtil.PROCESS_API_CREATE_ORDER_TPS_RT, () -> {
            return createOrder(userId, pair, direction, amount, walletType, orderType, price, stopPrice, operator, src);
        });
        if (!apiResponse.successful()) {
            metricsUtil.timerMetric(PROCESS_API_CREATE_ORDER_ERROR_TPS_RT, () -> {
                warn("createOrderWrapper下单错误:<apiResponse-{}>,<userId-{}>,<pair-{}>,<direction-{}>,<amount-{}>,<orderType{}>,<price-{}>,<stopPrice-{}>,<operator-{}>,<src-{}>",
                        JSON.toJSONString(apiResponse), userId, pair, direction, amount, orderType, price, stopPrice, operator, src);
            });
        }
        return apiResponse;
    }

    private ApiResponse createOrder(String userId,
                                    String pair,
                                    String direction,
                                    BigDecimal amount,
                                    String walletType,
                                    String orderType,
                                    BigDecimal price,
                                    BigDecimal stopPrice,
                                    String operator,
                                    String src) {
        try {
            // 是否被限流
            int rateLimiteFlag = rateLimiterUtil.tryAcquireByCreate(src, pair);
            if (rateLimiteFlag > 0) {
                return new ApiResponse().setCode(2037).setData(String.valueOf(rateLimiteFlag));
            }

            if (StringUtils.isEmpty(userId) || amount.compareTo(BigDecimal.ZERO) <= 0) {
                return err(1801);
            }
            TradeDirectionEnum directionEnum = TradeDirectionEnum.getByCodeVerifyCase(direction);
            if (directionEnum == null) {
                return err(2025);
            }
            // 订单类型 是否正确
            if (!POSSIBLE_TRADE_TYPES.contains(orderType)) {
                return err(INVALID_PARAM_CODE, ImmutableList.of("orderType"));
            }
            //1. 检验是否可以下单
            String tradeOpen = localCacheUtil.getSiteConfByKey("TRADE_OPEN", "true");
            if (!Boolean.TRUE.toString().equals(tradeOpen)) {
                return err(2027);
            }
            TradePairConfig tradePairConfig = localCacheUtil.getPairConfigByPair(pair);
            if (tradePairConfig == null) {
                return err(2002);
            }
            TradePair tradePair = new TradePair(pair);
            String exchangeCoin = tradePair.getExchangeCoin();
            CoinConfig coinConfig = localCacheUtil.getCoinConfigByType(exchangeCoin);
            if (coinConfig == null) {
                return err(4001);
            }
            BigDecimal minTradeAmt = coinConfig.getMinimumTradeAmount();
            if (amount.compareTo(minTradeAmt) < 0) {
                return err(1801);
            }
            BigDecimal currentPrice = Objects.nonNull(price) ? price : tradePriceService.getCurrentPrice(pair);
            BigDecimal minTradeVolume = tradePairConfig.getMinimumTradeVolume();
            if (currentPrice.compareTo(BigDecimal.ZERO) > 0 && Objects.nonNull(minTradeVolume)
                    && minTradeVolume.compareTo(BigDecimal.ZERO) > 0
                    && currentPrice.multiply(amount).compareTo(minTradeVolume) < 0) {
                return err(2050, ImmutableList.of(String.valueOf(minTradeVolume + tradePair.getAnchoredCoin())));
            }
            int amountPrecision = tradePairConfig.getAmountPrecision();
            if (amount.scale() > amountPrecision) {
                return err(2031, ImmutableList.of(String.valueOf(amountPrecision)));
            }
            //2.2 对于WALLET type参数值校验
            if (!POSSIBLE_WALLET_TYPES_CREATE_TRADE.contains(walletType)) {
                return err(INVALID_PARAM_CODE, ImmutableList.of("walletType"));
            }
            // 3. 开放整个交易度的交易，以及买卖单方向的设置
            if (!tradePairConfig.isSupportTrade()) {
                return err(2027);
            }
            // 4. 买卖单单独设置
            // 5. 对于最早开盘时间的判断，如果现在系统时间早于开盘时间，则不能下单
            boolean isBid = TradeDirectionEnum.isBid(direction);
            if (isBid) {
                if (!tradePairConfig.isSupportBid()) {
                    return err(2033);
                }
                if (tradePairConfig.getEarliestBidTime().after(new Date())) {
                    return err(2034);
                }
            } else {
                if (!tradePairConfig.isSupportAsk()) {
                    return err(2035);
                }
                if (tradePairConfig.getEarliestAskTime().after(new Date())) {
                    return err(2036);
                }
            }
            // 6. 余额检查,以及不同的orderType对应的参数合法性检查 - 针对不同类型的订单类型，余额检查的逻辑不一样
            BigDecimal costPrice;
            if (StringUtils.equals(TradeOrderTypeEnum.MARKET.getCode(), orderType)) {
                if (price != null) {
                    return err(2041);
                }
                costPrice = tradePriceService.getCurrentPrice(pair);
            } else {
                if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                    return new ApiResponse().setCode(75);
                }
                int precisionConfig = tradePairConfig.getDefaultPrecision();
                if (price.scale() > precisionConfig) {
                    return err(76);
                }
                if (price.compareTo(TRADE_ORDER_PRICE_MAX_DECIMAL) > 0) {
                    return err(2047);
                }
                costPrice = price;
                if (StringUtils.equals(TradeOrderTypeEnum.STOP_LIMIT.getCode(), orderType)) {
                    if (StopLimitOperatorEnum.getByCode(operator) == null) {
                        return err(INVALID_PARAM_CODE, ImmutableList.of("operator"));
                    }
                    if (stopPrice == null || stopPrice.compareTo(BigDecimal.ZERO) <= 0 || stopPrice.compareTo(TRADE_ORDER_PRICE_MAX_DECIMAL) > 0) {
                        return err(INVALID_PARAM_CODE, ImmutableList.of("stopPrice"));
                    }
                    // stop limit 下单数量限制检查
                    String userPairLimitOrder = stringRedisTemplate.opsForValue().get(keyForOrderCount(userId, pair));
                    int userPairLimitOrderMax = Integer.parseInt(localCacheUtil.getSiteConfByKey("USER_PAIR_LIMIT_ORDER_MAX", "10"));
                    if (StringUtils.isNotEmpty(userPairLimitOrder) && Integer.parseInt(userPairLimitOrder) >= userPairLimitOrderMax) {
                        return err(2042, ImmutableList.of(String.valueOf(userPairLimitOrderMax)));
                    }
                }
            }
            ExchangeAmountInfo exchangeInfo = TradeModelUtil.toExchangeInfo(tradePair, direction, amount, costPrice);
            BigDecimal cost = exchangeInfo.getReduceCoinAmount();
            // 余额检查和冻结
            String reduceCoinType = exchangeInfo.getReduceCoinType();
            UserCoinAccount userCoinAccount = exchangeBusiness.selectExAccountNoLockInMaster(BizUtil.buildAccountId(walletType, userId, reduceCoinType));
            boolean isEnough = userCoinAccount == null ? false : userCoinAccount.getCoinAmount().compareTo(cost) >= 0;
            if (!isEnough) {
                return err(1005, ImmutableList.of(reduceCoinType));
            }

            // 下单数量检查
            long limit = Long.parseLong(localCacheUtil.getSiteConfByKey(ORDER_LIMIT_PER_USER_KEY, "500"));
            if (!localCacheUtil.checkUserUnfinishedOrderCount(src, userId, pair, direction, limit)) {
                return err(1931, ImmutableList.of(String.valueOf(limit)));
            }

            // 7. 构建order 对象
            Timestamp d = new Timestamp(System.currentTimeMillis());
            String orderNo = IdGenerator.generateKey(userId).toString();
            TradeOrder tradeOrder = new TradeOrder();
            tradeOrder.setId(orderNo);
            tradeOrder.setUserId(userId);
            tradeOrder.setPair(pair);
            tradeOrder.setTotalAmount(amount);
            tradeOrder.setDealAmount(BigDecimal.ZERO);
            tradeOrder.setOrderType(orderType);
            tradeOrder.setTotalAnchorCoin(BigDecimal.ZERO);
            tradeOrder.setDirection(directionEnum.getCode());
            tradeOrder.setCreatedTime(d);
            tradeOrder.setUpdateTime(d);
            tradeOrder.setWalletType(walletType);
            tradeOrder.setSrc(src);

            // 由于当前委托而产生的冻结数目,对于市价单和未生效止盈止损单，并不会提前冻结资金，对于限价单需要有对应冻结资金*//*
            if (StringUtils.equals(TradeOrderTypeEnum.MARKET.getCode(), orderType)) {
                tradeOrder.setFrozenAmountByOrder(BigDecimal.ZERO);
                tradeOrder.setPrice(BigDecimal.ZERO);
                tradeOrder.setStatus(TradeOrderStatusEnum.NORMAL.getVal());
            } else {
                tradeOrder.setPrice(costPrice);
                if (StringUtils.equals(TradeOrderTypeEnum.STOP_LIMIT.getCode(), orderType)) {
                    tradeOrder.setFrozenAmountByOrder(BigDecimal.ZERO);
                    tradeOrder.setOperator(operator);
                    tradeOrder.setStopPrice(stopPrice);
                    tradeOrder.setStatus(STOP_LIMIT_NOT_EFFECTIVE.getVal());
                } else {
                    tradeOrder.setFrozenAmountByOrder(cost);
                    tradeOrder.setStatus(TradeOrderStatusEnum.NORMAL.getVal());
                }
            }

            // 7.1 首先设置为最普遍的交易对费率
            BigDecimal basePairFeeRate = directionEnum == TradeDirectionEnum.BID ? tradePairConfig.getBidFeeRate() : tradePairConfig.getAskFeeRate();
            if (inPlatformFreeUser(userId) || isPairFreeUser(userId, tradePairConfig.getPair()) || localCacheUtil.getSpecialPlatformAccountCache(userId)) {
                basePairFeeRate = basePairFeeRate.multiply(BigDecimal.ZERO);
                tradeOrder.setEnableBkkDiscount(true);
            } else {
                //获取特殊交易对，这部分不支持BKK抵扣手续费
                List<String> pairs = localCacheUtil.getPairBounsConfig();
                if (!CollectionUtils.isEmpty(pairs) && pairs.contains(pair)) {
                    tradeOrder.setEnableBkkDiscount(false);
                } else {
                    boolean useBkkDeduct = userProfileService.findUserProfileByOpenBkkWithhold(userId, OPEN_BKK_WITHHOLD);
                    tradeOrder.setEnableBkkDiscount(useBkkDeduct);
                }

                List<Project> projectInfoList = localCacheUtil.getProjectByUserId(userId);
                // 7.5 获取项目方等级,等级利率转换, 交易目标币种或者锚定币种任意配置一个即可
                int projectLevel = getProjectLevel(projectInfoList, tradePair.getExchangeCoin(), tradePair.getAnchoredCoin());
                if (projectLevel > 0) {
                    BigDecimal levelFeeRate = getLevelFeeRate(projectLevel);
                    if (levelFeeRate.compareTo(BigDecimal.ZERO) >= 0 && levelFeeRate.compareTo(BigDecimal.ONE) <= 0) {
                        basePairFeeRate = basePairFeeRate.multiply(levelFeeRate);
                    }
                }
            }
            tradeOrder.setFeeRate(basePairFeeRate);

            String inviteBy = localCacheUtil.getUserInvitorByUserId(userId);
            if ("NO_INVITOR".equalsIgnoreCase(inviteBy)) {
                tradeOrder.setReferredBy("");
            } else {
                tradeOrder.setReferredBy(inviteBy);
            }

            // 用户挂单，下单前检查是否大单自动吃单
            boolean fromMachine = BizUtil.isInterOrder(tradeOrder);
            boolean isOpenApi = BizUtil.isOpenApiOrder(tradeOrder);
            if (!isOpenApi && !fromMachine) {
                // todo jsong autoTradeStrategy(tradePair, pair, price, amount, tradeOrder.getOrderType(), orderNo, TradeDirectionEnum.isBid(tradeOrder.getDirection()));
            }

            // 非计划委托直接发送到撮合系统
            ApiResponse apiResponse = new ApiResponse();
            // 发送kafka消息
            TradeOrderCmd tradeOrderCmd = new TradeOrderCmd(tradeOrder, ProcessOrderTypeEnum.CREATE_ORDER.getCode());
            boolean result = exchangeKafkaUtil.sendMsgForApiExchangeOrder(tradeOrderCmd);
            if (result) {
                apiResponse.setData(orderNo);
                apiResponse.setMsg(LanguageUtils.getGlobalizationMessage("7042"));
            } else {
                apiResponse.setCode(-1);
            }
            return apiResponse;
        } catch (Exception e) {
            error("createOrder下单错误:<userId-{}>,<pair-{}>,<direction-{}>,<amount-{}>,<orderType{}>,<price-{}>,<stopPrice-{}>,<operator-{}>,<src-{}>",
                    userId, pair, direction, amount, orderType, price, stopPrice, operator, src, e);
        }
        return ApiResponse.failResult();
    }

    private ApiResponse cancelOrderWrapper(String userId, String orderNo, String src) {
        ApiResponse apiResponse = metricsUtil.timerMetricByRes(MetricsUtil.PROCESS_API_CANCEL_ORDER_TPS_RT, () -> {
            try {
                HintManager.getInstance().setMasterRouteOnly();
                TradeOrder tradeOrder = tradeOrderUnfinishedService.findOrderById(orderNo);
                if (tradeOrder == null) {
                    return err(CODE_ORDER_NOT_EXISTS).setCodeGlobalParams(Lists.newArrayList(orderNo));
                } else if (!StringUtils.equals(tradeOrder.getUserId(), userId)) {
                    return err(ApiConstant.CODE_ORDER_USERID_NOT_MATCH);
                }

                // 是否被限流
                int rateLimiteFlag = rateLimiterUtil.tryAcquireByCancel(src, tradeOrder.getPair());
                if (rateLimiteFlag > 0) {
                    return new ApiResponse().setCode(2037).setData(String.valueOf(rateLimiteFlag));
                }

                // 不能重复撤单
                boolean setting = localCacheUtil.apiCancelOrderIsProc(orderNo, true);
                if (setting) {
                    return err(RESULT_DUPLICATE);
                }

                return cancelOrder(tradeOrder);
            } catch (Exception e) {
                error("cancelOrder撤单错误:<userId-{}><orderNo-{}><src-{}>", userId, orderNo, src, e);
            } finally {
                HintManager.clear();
            }
            return ApiResponse.failResult();
        });
        if (!apiResponse.successful()) {
            metricsUtil.timerMetric(PROCESS_API_CANCEL_ORDER_ERROR_TPS_RT, () -> {
                warn("cancelOrderWrapper撤单错误: <apiResponse-{}><userId-{}><orderNo-{}><src-{}>",
                        JSON.toJSONString(apiResponse), userId, orderNo, src);
            });
        }
        return apiResponse;
    }

    private ApiResponse cancelOrder(TradeOrder tradeOrder) {
        TradeOrderCmd tradeOrderCmd = new TradeOrderCmd(tradeOrder, ProcessOrderTypeEnum.CANCEL_ORDER.getCode());
        boolean result = exchangeKafkaUtil.sendMsgForApiExchangeOrder(tradeOrderCmd);
        if (result) {
            localCacheUtil.cancelOrderUpdataOrderCount(tradeOrder.getUserId(), tradeOrder.getPair(), tradeOrder.getDirection());
            return ApiResponse.succResult().setData(tradeOrder.getId());
        } else {
            return ApiResponse.failResult();
        }
    }

    @Override
    public Class getVOClz() {
        return ExchangeServiceImpl.class;
    }

    /**
     * 组装key
     *
     * @param userId
     * @param pair
     * @return
     */
    private String keyForOrderCount(String userId, String pair) {
        return StringUtils.join(Lists.newArrayList(TradeAppConstant.APP_NAME_TRADE, "stop_limit", userId, pair), ":");
    }

    /**
     * 是平台用户，并且不是用于刷成交量的用户  BEIYONG_4
     *
     * @param userId
     * @return
     */
    private boolean inPlatformFreeUser(String userId) {
        List<String> platformUserIds = localCacheUtil.getPlatFormUser();
        return platformUserIds.contains(userId) && !StringUtils.equals(BEIYONG_4, userId);
    }

    private boolean isPairFreeUser(String userId, String pair) {
        String str = localCacheUtil.getSiteConfByKey(PAIR_FREE_FEE_USER_ID_LIST_KEY_PREFIX + pair, "[]");
        List<String> userIds = JSONArray.parseArray(str, String.class);
        return !CollectionUtils.isEmpty(userIds) && userIds.contains(userId);
    }

    private int getProjectLevel(List<Project> projectInfoList, String exchangeCoin, String anchoredCoin) {
        if (CollectionUtils.isEmpty(projectInfoList)) {
            return 0;
        }

        // 根据交易币以及锚定币获取最高等级
        return projectInfoList.stream()
                .filter(o -> exchangeCoin.equalsIgnoreCase(o.getCoinType()) || anchoredCoin.equalsIgnoreCase(o.getCoinType()))
                .map(Project::getLevel)
                .min(Integer::compareTo)
                .orElse(0);
    }

    private BigDecimal getLevelFeeRate(int level) {
        String val = localCacheUtil.getSiteConfByKey(SiteConfigConstants.KEY_LEVEL_FEE_RATE, Strings.EMPTY);
        List<LevelFeeRateInfo> levelFeeRateList = StringUtils.isEmpty(val) ? LevelFeeRateInfo.DEFAULT_LEVEL_FEE_RATE_LIST
                : JSON.parseArray(val, LevelFeeRateInfo.class);

        if (CollectionUtils.isEmpty(levelFeeRateList)) {
            return BigDecimal.ONE;
        }
        return levelFeeRateList.stream()
                .filter(o -> level == o.getLevel())
                .findFirst()
                .map(LevelFeeRateInfo::getFeeRate)
                .orElse(BigDecimal.ONE);
    }

    private boolean isRateLimitUserWhiteList(String userId) {
        String str = localCacheUtil.getSiteConfByKey(KEY_TRADE_RATE_LIMIT_WHITE_LIST, "");
        if (StringUtils.isNotEmpty(str)) {
            List<String> userIds = JSONArray.parseArray(str, String.class);
            return !CollectionUtils.isEmpty(userIds) && userIds.contains(userId);
        }
        return false;
    }

    /**
     * @param tradePair
     * @param pair
     * @param orderPrice 限价挂单价格
     * @param amount
     * @param orderType
     * @param orderNo
     * @param isBid      用户下单相反方向
     */
    private void autoTradeStrategy(TradePair tradePair, String pair, BigDecimal orderPrice, BigDecimal amount, String orderType, String orderNo, boolean isBid) {
        TradeBigOrderDTO tradeBigOrderDTO = null;
        try {
            // 方向需要反转，用户下买单，反向挂卖单接单
            isBid = !isBid;
            BigDecimal newPrice = tradePriceService.getCurrentPrice(pair);
            warn("大单吃单开始,pair<{}>,orderPrice<{}>,amount<{}>,newPrice<{}>", pair, orderPrice, amount, newPrice);
            // 获取配置
            String result = stringRedisTemplate.opsForValue().get(AUTO_TRADE_ORDER_KEY + pair);
            warn("大单吃单,redisResult<{}>", result);
            if (StringUtils.isEmpty(result)) {
                warn("大单吃单result为空");
                return;
            }
            tradeBigOrderDTO = JSONObject.parseObject(result, new TypeReference<TradeBigOrderDTO>() {
            });
            // 缓存配置时间超过1小时
            if (tradeBigOrderDTO.getTime() != null && System.currentTimeMillis() - tradeBigOrderDTO.getTime() > EFFECTIVE_TIME_RANGE) {
                return;
            }
            // 买吃单预算价格大于最新价，卖吃单预算价格小于最新价
            if (tradeBigOrderDTO.getBidTriggerTradePredictPrice().compareTo(newPrice) > 0 || tradeBigOrderDTO.getAskTriggerTradePredictPrice().compareTo(newPrice) < 0) {
                warn("大单吃单买或者卖价格超过最新价,pair<{}>,newPrice<{}>,bidPredictPrice<{}>,askPredictPrice<{}>", pair, newPrice, tradeBigOrderDTO.getBidTriggerTradePredictPrice(), tradeBigOrderDTO.getAskTriggerTradePredictPrice());
                return;
            }
            if (tradeBigOrderDTO.getTradeBigOrderGearList().isEmpty()) {
                return;
            }
            boolean isExec = checkIsExec(isBid, tradeBigOrderDTO, orderPrice, amount, orderType);
            if (!isExec) {
                warn("大单吃单不能成功执行,pair<{}>,isBid<{}>,orderPrice<{}>,amount<{}>,askPredictPrice<{}>,bidPredictPrice<{}>,bidNeedAmount<{}>,askNeedAmount<{}>",
                        pair, isBid, orderPrice, amount, tradeBigOrderDTO.getAskTriggerTradePredictPrice(),
                        tradeBigOrderDTO.getBidTriggerTradePredictPrice(), tradeBigOrderDTO.getAskTriggerTradePredictPrice(),
                        tradeBigOrderDTO.getBidTradeNeedAmount(), tradeBigOrderDTO.getAskTradeNeedAmount());
                return;
            }
            List<String> successOrderNos = new ArrayList<>();
            List<TradeBigOrderGear> tradeBigOrderGearList;
            if (isBid) {
                tradeBigOrderGearList = tradeBigOrderDTO.getTradeBigOrderGearList().stream().filter(orderGear -> orderGear.getTypes() == 1 && !orderGear.getDelFlag()).collect(Collectors.toList());
            } else {
                tradeBigOrderGearList = tradeBigOrderDTO.getTradeBigOrderGearList().stream().filter(orderGear -> orderGear.getTypes() == 2 && !orderGear.getDelFlag()).collect(Collectors.toList());
            }
            warn("大单吃单,tradeBigOrderGearList<{}>", tradeBigOrderGearList);
            if (!tradeBigOrderGearList.isEmpty()) {
                successOrderNos = execGearOrder(isBid, tradeBigOrderDTO, tradeBigOrderGearList, tradeBigOrderDTO.getUserId(), newPrice, tradePair, pair);
            }
            if (!successOrderNos.isEmpty()) {
                // 五分之一几率输出日志
                //                if (RandomUtil.randomOfEvery(5)){
                warn("userId<{}>,pair<{}>创建订单成功：successOrderNos<{}>,execIsBid<{}>", tradeBigOrderDTO.getUserId(), tradeBigOrderDTO.getPair(), successOrderNos, isBid);
                //                }
                // 缓存触发自动吃大单的订单号
                SuccessOrderRelationModel successOrderRelationModel = new SuccessOrderRelationModel();
                successOrderRelationModel.setOrderNo(orderNo);
                successOrderRelationModel.setIsBid(isBid);
                successOrderRelationModel.setSuccessOrderNos(successOrderNos);
                stringRedisTemplate.opsForValue().set(AUTO_TRADE_ORDER_RELATION_KEY + tradeBigOrderDTO.getPair() + "_" + isBid, JSONObject.toJSONString(successOrderRelationModel));
            }
        } catch (Exception e) {
            error("userId<{}>,pair<{}>,orderNo<{}>生成自动吃大单失败{}", tradeBigOrderDTO != null ? tradeBigOrderDTO.getUserId() : "null", pair, orderNo, e);
        }
    }

    private boolean checkIsExec(boolean isBid, TradeBigOrderDTO tradeBigOrderDTO, BigDecimal tradePrice, BigDecimal amount, String orderType) {
        boolean isExec = false;
        if (TradeOrderTypeEnum.MARKET.getCode().equals(orderType)) {
            // 只判断数量是否超过
            if (isBid) {
                if (tradeBigOrderDTO.getBidTradeNeedAmount().compareTo(BigDecimal.ZERO) > 0
                        && amount.compareTo(tradeBigOrderDTO.getBidTradeNeedAmount()) > 0) {
                    isExec = true;
                }
            } else {
                if (tradeBigOrderDTO.getAskTradeNeedAmount().compareTo(BigDecimal.ZERO) > 0
                        && amount.compareTo(tradeBigOrderDTO.getAskTradeNeedAmount()) > 0) {
                    isExec = true;
                }
            }
        } else if (TradeOrderTypeEnum.LIMIT.getCode().equals(orderType)) {
            // 现价判断价格和数量是否满足
            if (isBid) {
                if (tradeBigOrderDTO.getBidTradeNeedAmount().compareTo(BigDecimal.ZERO) <= 0
                        || tradeBigOrderDTO.getBidTriggerTradePredictPrice().compareTo(BigDecimal.ZERO) <= 0) {
                    return false;
                }
                if (tradePrice.compareTo(tradeBigOrderDTO.getBidTriggerTradePredictPrice()) <= 0
                        && amount.compareTo(tradeBigOrderDTO.getBidTradeNeedAmount()) > 0) {
                    isExec = true;
                }
            } else {
                if (tradeBigOrderDTO.getAskTradeNeedAmount().compareTo(BigDecimal.ZERO) <= 0
                        || tradeBigOrderDTO.getAskTriggerTradePredictPrice().compareTo(BigDecimal.ZERO) <= 0) {
                    return false;
                }
                if (tradePrice.compareTo(tradeBigOrderDTO.getAskTriggerTradePredictPrice()) >= 0
                        && amount.compareTo(tradeBigOrderDTO.getAskTradeNeedAmount()) > 0) {
                    isExec = true;
                }
            }
        }
        return isExec;
    }

    private List<String> execGearOrder(boolean isBid, TradeBigOrderDTO tradeBigOrderDTO, List<TradeBigOrderGear> tradeBigOrderGearList, String userId, BigDecimal newPrice, TradePair tradePair, String pair) {
        List<String> successOrderNos = new ArrayList<>();
        boolean isContinueOrder = true;
        BigDecimal totalLimitAmount;
        if (isBid) {
            totalLimitAmount = tradeBigOrderDTO.getBidTotalLimitAmount();
        } else {
            totalLimitAmount = tradeBigOrderDTO.getAskTotalLimitAmount();
        }
        // 买挂单总量
        BigDecimal orderTotalAmount = BigDecimal.ZERO;
        int orderCount = 0;
        // 计算出各个档位的挂单
        for (int i = 0; i < tradeBigOrderGearList.size(); i++) {
            orderCount++;
            // 档位有序
            TradeBigOrderGear orderGear = tradeBigOrderGearList.get(i);
            BigDecimal gearOrderPrice;
            TradeDirectionEnum tradeDirectionEnum;
            if (isBid) {
                //【买铺单价格】 = 【当前最新成交价】 * （1-【该档位价格百分比】）
                gearOrderPrice = newPrice.multiply(BigDecimal.ONE.subtract(orderGear.getGearPriceRate()));
                tradeDirectionEnum = TradeDirectionEnum.BID;
            } else {
                //【卖铺单价格】 = 【当前最新成交价】 * （1+【该档位价格百分比】）
                gearOrderPrice = newPrice.multiply(BigDecimal.ONE.add(orderGear.getGearPriceRate()));
                tradeDirectionEnum = TradeDirectionEnum.ASK;
            }
            BigDecimal amount = orderGear.getQuotaMax().divide(gearOrderPrice, 8, RoundingMode.DOWN);
            orderTotalAmount = orderTotalAmount.add(amount);
            // 超过深度数量，不进行挂单后续
            if (orderCount > MAX_ORDER_NUMBER) {
                isContinueOrder = false;
                warn("大单吃单超过最大下单数量pair<{}>,amount<{}>,MAX_ORDER_NUMBER<{}>", pair, amount, MAX_ORDER_NUMBER);
                break;
            }
            int status = createOrderByStrategy(successOrderNos, tradePair, pair, userId, amount, gearOrderPrice, tradeDirectionEnum.getCode());
            // 资产不足不进行后续挂单
            if (status == -2) {
                warn("大单吃单资产不足pair<{}>,amount<{}>,orderTotalAmount<{}>,totalLimitAmount<>", amount, orderTotalAmount, totalLimitAmount);
                isContinueOrder = false;
                break;
            }
        }

        // 是否继续往后挂单
        if (isContinueOrder && !tradeBigOrderGearList.isEmpty()) {
            List<String> tempResult = continueOrder(isBid, orderCount, tradeBigOrderDTO, tradeBigOrderGearList, tradeBigOrderDTO.getUserId(), newPrice, tradePair, pair, orderTotalAmount);
            successOrderNos.addAll(tempResult);
        }
        return successOrderNos;
    }

    private List<String> continueOrder(boolean isBid, int orderCount, TradeBigOrderDTO tradeBigOrderDTO, List<TradeBigOrderGear> tradeBigOrderGearList,
                                       String userId, BigDecimal newPrice, TradePair tradePair, String pair, BigDecimal orderTotalAmount) {
        List<String> successOrderNos = new ArrayList<>();
        int count = orderCount;
        // 最后一档基础
        TradeBigOrderGear orderGear = tradeBigOrderGearList.get(tradeBigOrderGearList.size() - 1);
        while (true) {
            if (count >= MAX_ORDER_NUMBER) {
                warn("继续挂单大单吃单超过最大下单数量pair<{}>,orderTotalAmount<{}>,orderCount<>", orderTotalAmount, orderCount);
                break;
            }
            if (isBid) {
                orderGear.setGearPriceRate(orderGear.getGearPriceRate().add(tradeBigOrderDTO.getBidTradeGearDifferentRate()));
                orderGear.setQuotaMax(orderGear.getQuotaMax().multiply(tradeBigOrderDTO.getBidTradeGearQuotaMultiple()));
            } else {
                orderGear.setGearPriceRate(orderGear.getGearPriceRate().add(tradeBigOrderDTO.getAskTradeGearDifferentRate()));
                orderGear.setQuotaMax(orderGear.getQuotaMax().multiply(tradeBigOrderDTO.getAskTradeGearQuotaMultiple()));
            }

            BigDecimal gearOrderPrice;
            TradeDirectionEnum tradeDirectionEnum;
            if (isBid) {
                //【买铺单价格】 = 【当前最新成交价】 * （1+【该档位价格百分比】）
                gearOrderPrice = newPrice.multiply(BigDecimal.ONE.subtract(orderGear.getGearPriceRate()));
                tradeDirectionEnum = TradeDirectionEnum.BID;
            } else {
                // 【卖铺单价格】 = 【当前最新成交价】 * （1-【该档位价格百分比】）
                gearOrderPrice = newPrice.multiply(BigDecimal.ONE.add(orderGear.getGearPriceRate()));
                tradeDirectionEnum = TradeDirectionEnum.ASK;
            }
            BigDecimal amount = orderGear.getQuotaMax().divide(gearOrderPrice, 8, RoundingMode.DOWN);
            orderTotalAmount = orderTotalAmount.add(amount);
            int status = createOrderByStrategy(successOrderNos, tradePair, pair, userId, amount, gearOrderPrice, tradeDirectionEnum.getCode());
            // 资产不足不进行后续挂单
            if (status == -2) {
                warn("继续挂单大单吃单资产不足pair<{}>,amount<{}>,orderTotalAmount<{}>,orderCount<>", amount, orderTotalAmount, orderCount);
                break;
            }
            count++;
        }
        return successOrderNos;
    }

    private Integer createOrderByStrategy(List<String> successOrderNos, TradePair tradePair, String pair, String userId, BigDecimal amount, BigDecimal gearOrderPrice, String directionEnum) {
        ExchangeAmountInfo exchangeInfo = TradeModelUtil.toExchangeInfo(tradePair, directionEnum, amount, gearOrderPrice);
        UserCoinAccount userCoinAccount = exchangeBusiness.selectExAccountNoLockInMaster(
                BizUtil.buildAccountId(UserWalletTypeEnum.WALLET.getCode(), userId, exchangeInfo.getReduceCoinType()));
        BigDecimal cost = exchangeInfo.getReduceCoinAmount();
        Timestamp d = new Timestamp(System.currentTimeMillis());
        TradeOrder tradeOrder = new TradeOrder();
        String orderNo = IdGenerator.generateKey(userId).toString();
        tradeOrder.setId(orderNo);
        tradeOrder.setUserId(userId);
        tradeOrder.setPair(pair);
        tradeOrder.setTotalAmount(amount);
        tradeOrder.setDealAmount(BigDecimal.ZERO);
        tradeOrder.setPrice(gearOrderPrice);
        tradeOrder.setFrozenAmountByOrder(cost);
        tradeOrder.setTotalAnchorCoin(BigDecimal.ZERO);
        tradeOrder.setDirection(directionEnum);
        tradeOrder.setCreatedTime(d);
        tradeOrder.setUpdateTime(d);
        tradeOrder.setWalletType(com.weibi.config.enums.UserWalletTypeEnum.WALLET.getCode());
        tradeOrder.setEnableBkkDiscount(false);
        tradeOrder.setFeeRate(BigDecimal.ZERO);
        tradeOrder.setSrc(TradeOrderSrcEnum.INTER.getCode());
        tradeOrder.setReferredBy("");
        tradeOrder.setOrderType(TradeOrderTypeEnum.LIMIT.getCode());
        tradeOrder.setStatus(TradeOrderStatusEnum.STOP_LIMIT_NOT_EFFECTIVE.getVal());
        String reduceCoinType = exchangeInfo.getReduceCoinType();
        boolean isEnough = userCoinAccount.getCoinAmount().compareTo(cost) >= 0;
        if (!isEnough) {
            error("userId<{}>,coin<{}>创建订单资产不足", userId, reduceCoinType);
            return -2;
        }
        // 下单 冻结账户
        ApiResponse<BalanceChangeRequest> response = null;//tradeOrderUnfinishedService.createOrderAndLockAmt(tradeOrder, true);
        if (response.successful()) {
            // 发送kafka消息
            TradeOrderCmd tradeOrderCmd = new TradeOrderCmd(tradeOrder, ProcessOrderTypeEnum.CREATE_ORDER.getCode());
            //exchangeKafkaUtil.sendMsgForApiCreateOrder(response.getData(), tradeOrderCmd);
        }

        if (!CommonResponseUtil.isSuccess(response)) {
            warn("userId<{}>,pair<{}>创建订单失败：code<{}>,msg<{}>", userId, pair, response.getCode(), LanguageUtils.getGlobalizationMessage(response));
            return -1;
        } else {
            // 概率输出日志
            if (RandomUtil.randomOfEvery(20)) {
                warn("userId<{}>,pair<{}>创建订单成功：orderNo<{}>", userId, pair, orderNo);
            }
            successOrderNos.add(orderNo);
            return 0;
        }
    }
}
