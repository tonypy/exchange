package com.weibi.exchange.core;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.weibi.config.constant.ApiConstant;
import com.weibi.config.enums.*;
import com.weibi.config.model.TradePair;
import com.weibi.config.model.trade.TradeDeal;
import com.weibi.config.model.trade.TradeOrder;
import com.weibi.config.model.trade.match.info.ExchangeAmountInfo;
import com.weibi.config.model.trade.match.info.MatchingExchangeInfo;
import com.weibi.config.model.trade.match.info.TradeFeeRateConfigInfo;
import com.weibi.config.util.TradeModelUtil;
import com.weibi.core.balance.delega.enums.BalanceChangeRoleEnum;
import com.weibi.core.balance.delega.enums.BalanceChangeTypeEnum;
import com.weibi.core.balance.delega.model.BalanceChangeRecord;
import com.weibi.core.balance.delega.model.BalanceChangeRequest;
import com.weibi.core.balance.delega.model.UserCoinAccount;
import com.weibi.core.commons.api.ApiResponse;
import com.weibi.core.commons.logging.ILogging;
import com.weibi.core.commons.util.IdGenerator;
import com.weibi.core.commons.util.NumberUtil;
import com.weibi.exchange.model.ExchangeResult;
import com.weibi.exchange.model.MatchingResult;
import com.weibi.exchange.util.BizUtil;
import com.weibi.exchange.util.LocalCacheUtil;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.weibi.core.balance.delega.constants.TransAppConstant.CODE_MARKET_ORDER_FAIL_BECAUSE_OF_DEPTH;
import static com.weibi.core.commons.api.CommonRespConstant.RESULT_CODE_AV_NOT_ENOUGH;

/**
 * 撮合交易
 *
 * @author jsong
 */
@Data
public class CoinTrader implements ILogging {
    /***交易对***/
    private String pair;
    /***当前币对处理线程***/
    private String threadName;
    /***当前线程变更版本号***/
    private volatile Long threadVersion = 0L;
    /***默认数量精度***/
    private int amountPrecision = 4;
    /***默认价格精度***/
    private int pricePrecision = 8;
    /***交易手续费账户***/
    private String userExchangeFeeAccount;
    /***最小成交量***/
    private BigDecimal minimumTradeAmount;
    /***本地缓存工具类***/
    private LocalCacheUtil localCacheUtil;
    /***当前交易器锁***/
    private Lock lock = new ReentrantLock();
    /***业务操作操作***/
    private ExchangeBusiness exchangeBusiness;
    /***本地订单缓存***/
    private Map<String, TradeOrder> idTradeOrderMap = new HashMap<>();
    /***订单队列是否加载完毕***/
    private AtomicBoolean ready = new AtomicBoolean(false);
    /***买单价格队列 降序***/
    private TreeMap<BigDecimal, MergeOrder> buyLimitPriceQueue = new TreeMap<>(Comparator.reverseOrder());
    /***卖单价格队列 升序***/
    private TreeMap<BigDecimal, MergeOrder> sellLimitPriceQueue = new TreeMap<>(Comparator.naturalOrder());
    /***买房盘口队列  price->amount 降序***/
    private TreeMap<BigDecimal, BigDecimal> buyPlateQueue = new TreeMap<>(Comparator.reverseOrder());
    /***卖方盘口队列 升序***/
    private TreeMap<BigDecimal, BigDecimal> sellPlateQueue = new TreeMap<>(Comparator.naturalOrder());

    public CoinTrader(String pair) {
        this.pair = pair;
    }

    /**
     * 处理订单
     *
     * @param tradeOrderCmd
     * @param processType
     */
    public ApiResponse processOrder(TradeOrderCmd tradeOrderCmd, ProcessOrderTypeEnum processType, Long currThreadVersion) {
        TradeOrder tradeOrder = tradeOrderCmd.getTradeOrder();
        String currentThreadName = BizUtil.getBizThreadName();
        if (StringUtils.isEmpty(this.threadName)) {
            this.threadName = currentThreadName;
        }
        if (!currentThreadName.equalsIgnoreCase(threadName)) {
            if (currThreadVersion < this.threadVersion) {
                error("严重错误处理消息线程不一致信息,丢弃老线程单条数据:p-<{}>ot-<{}>nt-<{}>ord-<{}>ov<{}>nv<{}>",
                        tradeOrderCmd.getPartition(), threadName, BizUtil.getBizThreadName(), JSON.toJSONString(tradeOrderCmd), currentThreadName, this.threadVersion);
                return ApiResponse.failResult();
            }
            try {
                Thread.sleep(2000);
                error("严重错误处理消息线程不一致信息,sleep更换线程:p-<{}>ot-<{}>nt-<{}>ord-<{}>", tradeOrderCmd.getPartition(), threadName, BizUtil.getBizThreadName(), JSON.toJSONString(tradeOrderCmd));
                this.threadName = currentThreadName;
            } catch (Exception e) {
                error("严重错误处理消息线程不一致信息，sleep更换线程异常:p-<{}>ot-<{}>nt-<{}>ord-<{}>", tradeOrderCmd.getPartition(), threadName, BizUtil.getBizThreadName(), JSON.toJSONString(tradeOrderCmd), e);
                return ApiResponse.failResult();
            }
            this.threadName = currentThreadName;
        }
        lock.lock();
        try {
            if (!this.pair.equalsIgnoreCase(tradeOrder.getPair())) {
                return ApiResponse.failResult();
            }
            if (!this.ready.get()) {
                initCoinTrader(pair);
            }
            if (ProcessOrderTypeEnum.CANCEL_ORDER.getCode().equalsIgnoreCase(processType.getCode())) {
                cancelOrder(tradeOrder, processType.getCode());
            } else {
                // 重置撮合订单状态为有效
                ApiResponse apiResponse = tradeOrder(tradeOrder, processType.getCode());
                if (!apiResponse.successful()) {
                    if (apiResponse.getCode() == ApiConstant.CODE_STOPLIMIT_CANNOT_CANCEL) {
                        error("激活计划委托订单不存在或状态不一致，丢弃订单:<{}><{}>", JSON.toJSONString(apiResponse), JSON.toJSONString(tradeOrder));
                    } else {
                        BizUtil.ACCOUNT_WARN_LOG.warn("撮合失败直接进行撤单操作:<{}><{}>", JSON.toJSONString(apiResponse), JSON.toJSONString(tradeOrder));
                        ExchangeResult exchangeResult = ExchangeResult.succResult(BalanceChangeTypeEnum.CANCEL_TRADE);
                        if (BizUtil.isActiveStopLimitOrder(tradeOrder)) {
                            removeMemoryOrder(tradeOrder, false);
                            tradeOrder.setStatus(TradeOrderStatusEnum.CANCEL_NOT_EFFECTIVE.getVal());
                            exchangeResult.addBizOrderFilledOrUpdatedOrAdded(tradeOrder, false);
                        } else {
                            tradeOrder.setStatus(TradeOrderStatusEnum.CANCEL.getVal());
                            exchangeResult.addBizOrderFilledOrUpdatedOrAdded(tradeOrder, true);
                        }
                        TradeExecutor.addExchangeResult(exchangeResult);
                    }
                }
            }
            return ApiResponse.succResult();
        } finally {
            lock.unlock();
            // 清除一级缓存
            TradeExecutor.clearSingleExchangeThreadLocal();
        }
    }

    /**
     * 撮合交易
     *
     * @param tradeOrder
     * @return
     */
    private ApiResponse tradeOrder(TradeOrder tradeOrder, String action) {
        // 订单幂等判断 不能重复处理
        if (localCacheUtil.tradeOrderIsProc(BizUtil.createOrderCacheKey(tradeOrder), true)) {
            error("重复处理撮合订单，请检查相关逻辑:<{}>", JSON.toJSONString(tradeOrder));
            return ApiResponse.succResult();
        }
        if (idTradeOrderMap.containsKey(tradeOrder.getId()) && !BizUtil.isActiveStopLimitOrder(tradeOrder)) {
            error("订单已经存在撮合队列，不能重复撮合，请检查相关逻辑:<{}>", JSON.toJSONString(tradeOrder));
            return ApiResponse.succResult();
        }
        if (tradeOrder.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0
                || tradeOrder.getTotalAmount().subtract(tradeOrder.getDealAmount()).compareTo(BigDecimal.ZERO) <= 0) {
            return ApiResponse.failResult();
        }
        if (!BizUtil.isMarketOrder(tradeOrder) && tradeOrder.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return ApiResponse.failResult();
        }

        // 撮合未成功 失败只有两种可能 市价单未吃满  或 成交价或数量错误 冻结余额不足
        TreeMap<BigDecimal, MergeOrder> limitPriceOrderList = TradeDirectionEnum.isBid(tradeOrder.getDirection()) ?
                sellLimitPriceQueue : buyLimitPriceQueue;
        ExchangeResult exchangeResult = tryMatchOnCreated(limitPriceOrderList, tradeOrder);
        if (!exchangeResult.successful()) {
            return ApiResponse.failResult()
                    .setCode(exchangeResult.getCode())
                    .setCodeGlobalParams(exchangeResult.getCodeGlobalParams());
        }

        // 清算失败
        ApiResponse clearingResponse = exchangeBusiness.clearingExchangeResult(exchangeResult);
        if (!clearingResponse.successful()) {
            return clearingResponse;
        }

        // 更新内存队列
        updateMemoryOrder(exchangeResult);
        // 添加撮合结果
        TradeExecutor.addExchangeResult(exchangeResult);
        return clearingResponse;
    }

    /**
     * 取消订单 清空内存队列 删除数据库记录 退费 发送消息
     *
     * @param tradeOrder
     * @return
     */
    private ApiResponse cancelOrder(TradeOrder tradeOrder, String action) {
        if (BizUtil.isMarketOrder(tradeOrder)) {
            BizUtil.EXCHANGE_WARN_LOG.warn("市价单不存在撤单:<{}>", JSON.toJSONString(tradeOrder));
        }
        TradeOrder cancelOrder = idTradeOrderMap.get(tradeOrder.getId());
        if (cancelOrder == null) {
            BizUtil.EXCHANGE_WARN_LOG.warn("撤销订单不存在:<{}><{}>", JSON.toJSONString(tradeOrder), JSON.toJSONString(cancelOrder));
            return ApiResponse.succResult();
        }
        if (cancelOrder.isStatusEnd()) {
            error("撤单订单已完成，不能撤销:<{}><{}>" + JSON.toJSONString(tradeOrder), JSON.toJSONString(cancelOrder));
            return ApiResponse.succResult();
        }

        ExchangeResult exchangeResult = ExchangeResult.succResult(BalanceChangeTypeEnum.CANCEL_TRADE);
        exchangeResult.setOldFocusedOrder(cancelOrder);

        // 如果存在冻结费用 机器人不冻结费用 避免下单锁竞争
        if (!NumberUtil.emptyOrZero(cancelOrder.getFrozenAmountByOrder())) {
            TradePair tradePair = new TradePair(cancelOrder.getPair());
            boolean isBid = TradeDirectionEnum.isBid(cancelOrder.getDirection());
            String frozenCoin = isBid ? tradePair.getAnchoredCoin() : tradePair.getExchangeCoin();
            BigDecimal frozenAmtByOrder = cancelOrder.getFrozenAmountByOrder();
            boolean isMargin = UserWalletTypeEnum.isMargin(cancelOrder.getOrderType());
            String walletType = isMargin ? UserWalletTypeEnum.MARGIN.getCode() : UserWalletTypeEnum.WALLET.getCode();
            String remark = BizUtil.remarkForOrder(cancelOrder);
            // 组装 余额变更信息
            BalanceChangeRecord balanceChangeRecord = new BalanceChangeRecord.Builder()
                    .userId(cancelOrder.getUserId())
                    .coinType(frozenCoin)
                    .availableBalanceChange(frozenAmtByOrder)
                    .frozenBalanceChange(frozenAmtByOrder.negate())
                    .walletType(walletType)
                    .remark(remark)
                    .build();
            BalanceChangeRequest balanceChangeRequest = new BalanceChangeRequest();
            balanceChangeRequest.setBusinessType(BalanceChangeTypeEnum.CANCEL_TRADE.getCode());
            balanceChangeRequest.setBusinessKey(cancelOrder.getId());
            balanceChangeRequest.setBusinessTime(new Date());
            balanceChangeRequest.setChangeRecords(Lists.newArrayList(balanceChangeRecord));
            exchangeResult.setBalanceChangeReqBeforeMatch(balanceChangeRequest);
        }

        ApiResponse cancelResponse = exchangeBusiness.clearingExchangeResult(exchangeResult);
        if (!cancelResponse.successful()) {
            return ApiResponse.failResult();
        }

        // 修改订单状态
        cancelOrder.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        if (BizUtil.isStopLimitOrder(cancelOrder)) {
            if (TradeOrderStatusEnum.NORMAL.getVal() == cancelOrder.getStatus() || TradeOrderStatusEnum.PARTITIAL_FILLED.getVal() == cancelOrder.getStatus()) {
                cancelOrder.setStatus(TradeOrderStatusEnum.CANCEL_EFFECTIVE.getVal());
            }
            if (TradeOrderStatusEnum.STOP_LIMIT_NOT_EFFECTIVE.getVal() == cancelOrder.getStatus()) {
                cancelOrder.setStatus(TradeOrderStatusEnum.CANCEL_NOT_EFFECTIVE.getVal());
            }
        } else {
            cancelOrder.setStatus(TradeOrderStatusEnum.CANCEL.getVal());
        }
        exchangeResult.addBizOrderFilledOrUpdatedOrAdded(cancelOrder, false);
        exchangeResult.setNewFocusedOrder(cancelOrder);

        //移除内存队列和 把撮合结果存储内存队列
        removeMemoryOrder(cancelOrder, true);
        // 添加撮合结果
        TradeExecutor.addExchangeResult(exchangeResult);
        return ApiResponse.succResult();
    }

    /**
     * 撮合订单
     *
     * @param lpList
     * @param focusedOrder
     * @return
     */
    private ExchangeResult tryMatchOnCreated(TreeMap<BigDecimal, MergeOrder> lpList, TradeOrder focusedOrder) {
        ExchangeResult exchangeResult = ExchangeResult.succResult(BalanceChangeTypeEnum.CREATE_TRADE);
        exchangeResult.setOldFocusedOrder(focusedOrder);
        TradeOrder newFocusedOrder = focusedOrder;
        boolean isBid = TradeDirectionEnum.isBid(newFocusedOrder.getDirection());

        // 计划委托下单不进行撮合 直接落库和写入内存队列
        if (BizUtil.isCreateStopLimitOrder(focusedOrder)) {
            exchangeResult.addBizOrderFilledOrUpdatedOrAdded(newFocusedOrder, true);
            exchangeResult.setNewFocusedOrder(newFocusedOrder);
            return exchangeResult;
        }

        // 触发止盈止损
        if (BizUtil.isActiveStopLimitOrder(focusedOrder)) {
            TradeOrder tradeOrder = idTradeOrderMap.get(focusedOrder.getId());
            if (tradeOrder == null) {
                exchangeResult.setCode(ApiConstant.CODE_STOPLIMIT_CANNOT_CANCEL);
                return exchangeResult;
            }
            if (tradeOrder.getStatus() != TradeOrderStatusEnum.STOP_LIMIT_NOT_EFFECTIVE.getVal()) {
                exchangeResult.setCode(ApiConstant.CODE_STOPLIMIT_CANNOT_CANCEL);
                return exchangeResult;
            }
        }

        // 尝试撮合前检查逻辑,一般是检查余额是否充足, fail fast
        exchangeResult = commonCheckAvAndFrozeBeforeMatch(exchangeResult, focusedOrder);
        if (!exchangeResult.successful()) {
            return exchangeResult;
        }

        Iterator<Map.Entry<BigDecimal, MergeOrder>> mergeOrderIterator = lpList.entrySet().iterator();
        boolean exitLoop = false;
        while (!exitLoop && mergeOrderIterator.hasNext()) {
            Map.Entry<BigDecimal, MergeOrder> entry = mergeOrderIterator.next();
            MergeOrder mergeOrder = entry.getValue();
            Iterator<TradeOrder> orderIterator = mergeOrder.iterator();
            if (!mergeOrder.isValid()) {
                mergeOrderIterator.remove();
                continue;
            }
            if (!BizUtil.isMarketOrder(newFocusedOrder)
                    && ((isBid && mergeOrder.getPrice().compareTo(newFocusedOrder.getPrice()) > 0)
                    || (!isBid && mergeOrder.getPrice().compareTo(newFocusedOrder.getPrice()) < 0))) {
                break;
            }

            while (orderIterator.hasNext()) {
                TradeOrder matchOrder = orderIterator.next();
                if (matchOrder.isStatusEnd()) {
                    orderIterator.remove();
                    error("撮合订单已失效，移除:<{}>", JSON.toJSONString(matchOrder));
                    continue;
                }
                boolean isStopLimitOrder = BizUtil.isStopLimitOrder(matchOrder);
                if (isStopLimitOrder && (matchOrder.getStatus() == TradeOrderStatusEnum.STOP_LIMIT_NOT_EFFECTIVE.getVal()
                        || matchOrder.getStatus() == TradeOrderStatusEnum.STOP_LIMIT_ACTIVE_FAILED.getVal())) {
                    continue;
                }

                MatchingResult matchingResult = processMatch(newFocusedOrder, matchOrder);
                if (matchingResult.successful()) {
                    exchangeResult.getMatchingResults().add(matchingResult);
                    exchangeResult.addBizOrderFilledOrUpdatedOrAdded(isBid ? matchingResult.getNewAskOrder() : matchingResult.getNewBidOrder(), false);
                    newFocusedOrder = isBid ? matchingResult.getNewBidOrder() : matchingResult.getNewAskOrder();
                    if (newFocusedOrder.isStatusEnd()) {
                        exitLoop = true;
                        break;
                    }
                } else if (matchingResult.getCode() == RESULT_CODE_AV_NOT_ENOUGH) {
                    exitLoop = true;
                    break;
                } else {
                    exchangeResult.setCode(matchingResult.getCode());
                    return exchangeResult;
                }
            }
        }

        // 如果订单未完成 市价单更新为完成 非市价单呢更新maker手续费
        if (!newFocusedOrder.isStatusEnd()) {
            if (BizUtil.isMarketOrder(newFocusedOrder)) {
                // 如果部分成交 强制设置为完成，能成交多少成交多少  否则深度不够返回失败
                if (newFocusedOrder.getDealAmount().compareTo(BigDecimal.ZERO) > 0) {
                    newFocusedOrder.setStatus(TradeOrderStatusEnum.FILLED.getVal());
                } else {
                    exchangeResult.setCode(CODE_MARKET_ORDER_FAIL_BECAUSE_OF_DEPTH);
                    return exchangeResult;
                }
            } else {
                BigDecimal currentRate = newFocusedOrder.getFeeRate();
                if (currentRate.compareTo(BigDecimal.ZERO) > 0) {
                    newFocusedOrder.setFeeRate(currentRate.multiply(localCacheUtil.getTradeFeeRateConfigInfo().getMakerDiscount()));
                    newFocusedOrder.setUpdateTime(new Timestamp(System.currentTimeMillis()));
                }
            }
        }

        // 激活订单添加更新队列 其他订单 加入插入队列
        exchangeResult.addBizOrderFilledOrUpdatedOrAdded(newFocusedOrder, !BizUtil.isActiveStopLimitOrder(newFocusedOrder));
        exchangeResult.setNewFocusedOrder(newFocusedOrder);
        return exchangeResult;
    }

    /**
     * 增加限价订单到队列，买入单按从价格高到低排，卖出单按价格从低到高排
     *
     * @param tradeOrder
     */
    private void addLimitPriceOrder(TradeOrder tradeOrder) {
        if (BizUtil.isMarketOrder(tradeOrder)) {
            return;
        }
        TradeOrder oldTradeOrder = idTradeOrderMap.get(tradeOrder.getId());
        if (oldTradeOrder != null) {
            updateTradeOrder(oldTradeOrder, tradeOrder);
            return;
        }

        TreeMap<BigDecimal, MergeOrder> list = TradeDirectionEnum.isBid(tradeOrder.getDirection())
                ? buyLimitPriceQueue : sellLimitPriceQueue;
        MergeOrder mergeOrder = list.get(tradeOrder.getPrice());
        if (mergeOrder == null) {
            mergeOrder = new MergeOrder();
            mergeOrder.add(tradeOrder);
            list.put(tradeOrder.getPrice(), mergeOrder);
        } else {
            mergeOrder.add(tradeOrder);
        }
        idTradeOrderMap.put(tradeOrder.getId(), tradeOrder);

        // 处理盘口信息
        addPlateQueue(tradeOrder);
    }

    /**
     * 查询交易器里的订单
     *
     * @param orderId
     * @param orderType
     * @param direction
     * @return
     */
    private TradeOrder findOrder(String orderId, String orderType, String direction) {
        TreeMap<BigDecimal, MergeOrder> list = TradeDirectionEnum.isBid(direction) ?
                this.buyLimitPriceQueue : this.sellLimitPriceQueue;
        Iterator<Map.Entry<BigDecimal, MergeOrder>> mergeOrderIterator = list.entrySet().iterator();
        while (mergeOrderIterator.hasNext()) {
            Map.Entry<BigDecimal, MergeOrder> entry = mergeOrderIterator.next();
            MergeOrder mergeOrder = entry.getValue();
            Iterator<TradeOrder> orderIterator = mergeOrder.iterator();
            while ((orderIterator.hasNext())) {
                TradeOrder order = orderIterator.next();
                if (order.getId().equalsIgnoreCase(orderId)) {
                    return order;
                }
            }
        }
        return null;
    }

    /**
     * 处理两个匹配的委托订单
     *
     * @param focusedOrder 焦点单
     * @param matchOrder   匹配单
     * @return
     */
    private MatchingResult processMatch(TradeOrder focusedOrder, TradeOrder matchOrder) {
        boolean isBid = TradeDirectionEnum.isBid(focusedOrder.getDirection());
        TradeOrder bidOrder = isBid ? focusedOrder : matchOrder;
        TradeOrder askOrder = isBid ? matchOrder : focusedOrder;

        // 计算成交价
        BigDecimal dealPrice;
        if (BizUtil.isMarketOrder(focusedOrder)) {
            dealPrice = matchOrder.getPrice();
        } else {
            dealPrice = isBid ? askOrder.getPrice() : bidOrder.getPrice();
        }
        if (dealPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return MatchingResult.failResult();
        }
        //计算成交量
        BigDecimal bidOrderRemain = bidOrder.getTotalAmount().subtract(bidOrder.getDealAmount());
        BigDecimal askMaxDealAmt = askOrder.getTotalAmount().subtract(askOrder.getDealAmount());
        BigDecimal dealAmount = bidOrderRemain.compareTo(askMaxDealAmt) <= 0 ? bidOrderRemain : askMaxDealAmt;

        // 如果是市价单匹配 则计算余额是否足够 输出成交量
        if (BizUtil.isMarketOrder(focusedOrder) && dealAmount.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal totalPrice = isBid ? dealAmount.multiply(dealPrice) : dealAmount;
            TradePair tradePair = new TradePair(focusedOrder.getPair());
            // 市价单取本地缓存价格判断
            BigDecimal userCoinAmount = BigDecimal.ZERO;
            if (focusedOrder.getDealAmount().compareTo(BigDecimal.ZERO) == 0) {
                UserCoinAccount userCoinAccount = exchangeBusiness.getAndLockUserCoinAccountByCache(focusedOrder.getWalletType(),
                        focusedOrder.getUserId(),
                        isBid ? tradePair.getAnchoredCoin() : tradePair.getExchangeCoin());
                userCoinAmount = userCoinAccount != null ? userCoinAccount.getCoinAmount() : BigDecimal.ZERO;
            } else {
                userCoinAmount = TradeExecutor.getLocalCurrentReducecoinav();
            }

            // 判断余额是否足够
            if (userCoinAmount.compareTo(BigDecimal.ZERO) <= 0) {
                return MatchingResult.failResult(RESULT_CODE_AV_NOT_ENOUGH);
            } else if (userCoinAmount.compareTo(totalPrice) < 0) {
                dealAmount = isBid ? userCoinAmount.divide(dealPrice, amountPrecision, RoundingMode.DOWN)
                        : userCoinAmount;
                if (!BizUtil.canOrderMatch(minimumTradeAmount, dealAmount, dealPrice)) {
                    return MatchingResult.failResult(RESULT_CODE_AV_NOT_ENOUGH);
                }
            }
            // 更新本地余额
            totalPrice = isBid ? dealAmount.multiply(dealPrice) : dealAmount;
            TradeExecutor.setLocalCurrentReducecoinav(userCoinAmount.subtract(totalPrice));
        }
        if (dealAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return MatchingResult.failResult();
        }

        MatchingExchangeInfo bidExchangeInfo = buildExchangeAmountInfo(bidOrder, dealPrice, dealAmount);
        MatchingExchangeInfo askExchangeInfo = buildExchangeAmountInfo(askOrder, dealPrice, dealAmount);
        TradeOrder afterBidOrder = buildAfterOrderAfterDeal(bidOrder, bidExchangeInfo);
        TradeOrder afterAskOrder = buildAfterOrderAfterDeal(askOrder, askExchangeInfo);

        TradeDeal tradeDeal = new TradeDeal();
        String dealId = IdGenerator.generateKey(isBid ? bidOrder.getUserId() : askOrder.getUserId()).toString();
        tradeDeal.setId(dealId);
        tradeDeal.setBuyerId(bidOrder.getUserId());
        tradeDeal.setSellerId(askOrder.getUserId());
        tradeDeal.setPrice(dealPrice);
        tradeDeal.setDealAmount(dealAmount);
        tradeDeal.setPair(askOrder.getPair());
        tradeDeal.setDealDirection(isBid ? TradeDealDirectionEnum.BUY.getCode() : TradeDealDirectionEnum.SELL.getCode());
        tradeDeal.setBuyerFeeCoinType(bidExchangeInfo.getRealFeeCoinType());
        tradeDeal.setBuyerFee(bidExchangeInfo.getRealFeeAmt());
        tradeDeal.setBuyerSrc(bidOrder.getSrc());
        tradeDeal.setRefBuyOrder(bidOrder.getId());
        tradeDeal.setBuyerReferredBy(bidOrder.getReferredBy());
        tradeDeal.setSellerFeeCoinType(askExchangeInfo.getRealFeeCoinType());
        tradeDeal.setSellerFee(askExchangeInfo.getRealFeeAmt());
        tradeDeal.setSellerSrc(askOrder.getSrc());
        tradeDeal.setRefSellOrder(askOrder.getId());
        tradeDeal.setSellerReferredBy(askOrder.getReferredBy());
        tradeDeal.setCreatedTime(new Date());

        MatchingResult matchingResult = MatchingResult.succResult();
        matchingResult.setTradeDeal(tradeDeal);
        matchingResult.setNewBidOrder(afterBidOrder);
        matchingResult.setNewAskOrder(afterAskOrder);
        //matchingResult.setBidExchangeInfo(bidExchangeInfo);
        //matchingResult.setAskExchangeInfo(askExchangeInfo);

        BalanceChangeRequest matchRequest = new BalanceChangeRequest();
        matchingResult.setMatchBalanceChangeRequest(matchRequest);
        matchRequest.setBusinessType(BalanceChangeTypeEnum.TRADE_DEAL.getCode());
        matchRequest.setBusinessKey(tradeDeal.getId());
        matchRequest.setBusinessTime(tradeDeal.getCreatedTime());
        matchRequest.setChangeRecords(new ArrayList<>());
        // 计算余额变动记录
        calBalanceRecordsWhenMatch(matchingResult, bidExchangeInfo, afterBidOrder);
        calBalanceRecordsWhenMatch(matchingResult, askExchangeInfo, afterAskOrder);

        return matchingResult;
    }

    private int getLimitPriceOrderCount(String direction) {
        int count = 0;
        TreeMap<BigDecimal, MergeOrder> queue = TradeDirectionEnum.isBid(direction)
                ? buyLimitPriceQueue : sellLimitPriceQueue;
        Iterator<Map.Entry<BigDecimal, MergeOrder>> mergeOrderIterator = queue.entrySet().iterator();
        while (mergeOrderIterator.hasNext()) {
            Map.Entry<BigDecimal, MergeOrder> entry = mergeOrderIterator.next();
            MergeOrder mergeOrder = entry.getValue();
            count += mergeOrder.size();
        }
        return count;
    }

    /**
     * 计算交易和计算手续费
     *
     * @param tradeOrder
     * @param dealPrice
     * @param dealAmount
     * @return
     */
    private MatchingExchangeInfo buildExchangeAmountInfo(TradeOrder tradeOrder, BigDecimal dealPrice, BigDecimal dealAmount) {
        //1.  根据成交，构造对应扣减信息
        boolean isBid = TradeDirectionEnum.isBid(tradeOrder.getDirection());
        boolean isMargin = UserWalletTypeEnum.isMargin(tradeOrder.getWalletType());
        String pair = tradeOrder.getPair();

        MatchingExchangeInfo exchangeInfo = new MatchingExchangeInfo(
                tradeOrder.getUserId(), pair, dealPrice, dealAmount, tradeOrder.getFeeRate(), isBid, isMargin);
        exchangeInfo.setRealFeeAmt(exchangeInfo.getNormalGainAmt().multiply(exchangeInfo.getFeeRate()));
        exchangeInfo.setRealFeeCoinType(exchangeInfo.getNormalGainCoinType());
        if (exchangeInfo.getRealFeeAmt().compareTo(BigDecimal.ZERO) > 0) {
            boolean isDeduct = tradeOrder.getEnableBkkDiscount();
            exchangeInfo = refreshFeeInfoBasedOnBkkDeduct(exchangeInfo, isDeduct);
        }
        return exchangeInfo;
    }

    /**
     * 根据交易处理账务信息
     *
     * @param matchingResult
     * @param exchangeInfo
     * @param order
     */
    private void calBalanceRecordsWhenMatch(MatchingResult matchingResult, MatchingExchangeInfo exchangeInfo, TradeOrder order) {
        boolean isMargin = UserWalletTypeEnum.isMargin(order.getWalletType());
        boolean isBid = TradeDirectionEnum.isBid(order.getDirection());
        String walletType = isMargin ? UserWalletTypeEnum.MARGIN.getCode() : UserWalletTypeEnum.WALLET.getCode();

        String actorRole = isBid ? BalanceChangeRoleEnum.TRADE_DEAL_BUYER.getCode() : BalanceChangeRoleEnum.TRADE_DEAL_SELLER.getCode();
        String actorFeeRole = isBid ? BalanceChangeRoleEnum.TRADE_DEAL_BUYER_FEE.getCode() : BalanceChangeRoleEnum.TRADE_DEAL_SELLER_FEE.getCode();
        String actorFeePublicRole = isBid ? BalanceChangeRoleEnum.TRADE_DEAL_PUBLIC_FROM_BUYER_FEE.getCode() : BalanceChangeRoleEnum.TRADE_DEAL_PUBLIC_FROM_SELLER_FEE.getCode();

        TradePair pair = new TradePair(exchangeInfo.getPair());
        String userId = exchangeInfo.getUserId();
        BigDecimal anchorReduceByDealAmtFroz = exchangeInfo.getNormalReduceAmt();
        BigDecimal exchangeGainByDealAmtAv = exchangeInfo.getNormalGainAmt();
        String remark = TradeModelUtil.baseRemarkForDealInfo(exchangeInfo);
        List<BalanceChangeRecord> balanceChangeRecords = matchingResult.getMatchBalanceChangeRequest().getChangeRecords();

        BigDecimal reduceAvailableBalanceChange = BizUtil.isMarketOrder(order)
                ? anchorReduceByDealAmtFroz.negate() : BigDecimal.ZERO;
        BigDecimal reduceFrozenBalanceChange = BizUtil.isMarketOrder(order)
                ? BigDecimal.ZERO : anchorReduceByDealAmtFroz.negate();

        BalanceChangeRecord transDealAnchorInfo = new BalanceChangeRecord.Builder()
                .userId(userId).coinType(exchangeInfo.getNormalGainCoinType())
                .userRole(actorRole)
                .availableBalanceChange(exchangeGainByDealAmtAv)
                .walletType(walletType)
                .frozenBalanceChange(BigDecimal.ZERO)
                .remark(remark)
                .build();
        BalanceChangeRecord transDealExchangeInfo = new BalanceChangeRecord.Builder()
                .userId(userId).coinType(exchangeInfo.getNormalReduceCoinType())
                .userRole(actorRole)
                .availableBalanceChange(reduceAvailableBalanceChange)
                .walletType(walletType)
                .remark(remark)
                .frozenBalanceChange(reduceFrozenBalanceChange).build();
        balanceChangeRecords.add(transDealAnchorInfo);
        balanceChangeRecords.add(transDealExchangeInfo);

        // 收益方币种手续费
        if (!NumberUtil.emptyOrZero(exchangeInfo.getRealFeeAmt())) {
            BalanceChangeRecord transDealFeeCoinAmt = new BalanceChangeRecord.Builder()
                    .userId(userId).coinType(exchangeInfo.getRealFeeCoinType())
                    .walletType(walletType)
                    .userRole(actorFeeRole)
                    .remark(remark + TradeModelUtil.FEE_INDICATOR)
                    .availableBalanceChange(exchangeInfo.getRealFeeAmt().negate())
                    .frozenBalanceChange(BigDecimal.ZERO).build();
            BalanceChangeRecord transPublicFeeCoinAmt = new BalanceChangeRecord.Builder()
                    .userId(userExchangeFeeAccount)
                    .coinType(exchangeInfo.getRealFeeCoinType())
                    .walletType(walletType)
                    .userRole(actorFeePublicRole)
                    .remark(remark + TradeModelUtil.FEE_INDICATOR)
                    .availableBalanceChange(exchangeInfo.getRealFeeAmt())
                    .frozenBalanceChange(BigDecimal.ZERO).build();
            balanceChangeRecords.add(transDealFeeCoinAmt);
            matchingResult.getPublicRealFeeRecords().add(transPublicFeeCoinAmt);
            //balanceChangeRecords.add(transPublicFeeCoinAmt);
        }

        // 最后对于非市价买单来说，如果成交量完成了，但是成交价小于下单价，则要返回冻结额度
        if (isBid && !BizUtil.isMarketOrder(order)
                && order.getStatus() == TradeOrderStatusEnum.FILLED.getVal()
                && order.getFrozenAmountByOrder().compareTo(BigDecimal.ZERO) != 0) {
            BalanceChangeRecord bidReturnFrozenWhenFinishIfNecc = new BalanceChangeRecord.Builder()
                    .userId(userId).coinType(pair.getAnchoredCoin())
                    .userRole(BalanceChangeRoleEnum.TRADE_DEAL_BUYER_RETURN.getCode())
                    .walletType(walletType)
                    .remark(remark)
                    .availableBalanceChange(order.getFrozenAmountByOrder())
                    .frozenBalanceChange(order.getFrozenAmountByOrder().negate()).build();
            balanceChangeRecords.add(bidReturnFrozenWhenFinishIfNecc);
        }
    }

    /**
     * 根据交易计算订单信息
     *
     * @param tradeOrder
     * @param exchangeInfo
     * @return
     */
    private TradeOrder buildAfterOrderAfterDeal(TradeOrder tradeOrder, MatchingExchangeInfo exchangeInfo) {
        TradeOrder newOrder = new TradeOrder();
        BeanUtils.copyProperties(tradeOrder, newOrder);

        boolean isBid = TradeDirectionEnum.isBid(tradeOrder.getDirection());
        BigDecimal newRemainFrozenAmtByOrder = newOrder.getFrozenAmountByOrder().subtract(exchangeInfo.getNormalReduceAmt());
        if (!BizUtil.isMarketOrder(tradeOrder)) {
            newOrder.setFrozenAmountByOrder(newRemainFrozenAmtByOrder);
        }

        BigDecimal totalAnchorCoinChange = isBid ? exchangeInfo.getNormalReduceAmt() : exchangeInfo.getNormalGainAmt();
        newOrder.setTotalAnchorCoin(TradeModelUtil.dealAmtSupportNull(newOrder.getTotalAnchorCoin()).add(totalAnchorCoinChange));
        newOrder.setStatus(TradeOrderStatusEnum.PARTITIAL_FILLED.getVal());
        newOrder.setUpdateTime(new Timestamp(System.currentTimeMillis()));

        BigDecimal beforeDealAmt = newOrder.getDealAmount() == null ? BigDecimal.ZERO : newOrder.getDealAmount();
        BigDecimal afterDealAmt = beforeDealAmt.add(exchangeInfo.getDealAmt());
        newOrder.setDealAmount(afterDealAmt);
        if (afterDealAmt.compareTo(newOrder.getTotalAmount()) >= 0) {
            newOrder.setStatus(TradeOrderStatusEnum.FILLED.getVal());
        }

        return newOrder;
    }

    /**
     * 更新内存队列
     *
     * @param exchangeResult
     */
    private void updateMemoryOrder(ExchangeResult exchangeResult) {
        if (exchangeResult.successful()) {
            // 添加到队列
            if (!exchangeResult.getNewFocusedOrder().isStatusEnd()) {
                addLimitPriceOrder(exchangeResult.getNewFocusedOrder());
            }

            // 更新内存订单
            exchangeResult.getBizOrderUpdated().forEach(item -> {
                TradeOrder memTradeOrder = idTradeOrderMap.get(item.getId());
                if (memTradeOrder != null) {
                    updateTradeOrder(memTradeOrder, item);
                }
            });
            // 删除内存订单
            exchangeResult.getBizOrderDeleted().forEach(item -> {
                removeMemoryOrder(item, true);
            });
        }
    }

    /**
     * 删除内存队列订单
     *
     * @param tradeOrder
     */
    private void removeMemoryOrder(TradeOrder tradeOrder, boolean isCalPlate) {
        // 如果内存队列没有 则直接返回
        if (idTradeOrderMap.get(tradeOrder.getId()) == null) {
            return;
        }
        TreeMap<BigDecimal, MergeOrder> list = TradeDirectionEnum.isBid(tradeOrder.getDirection())
                ? buyLimitPriceQueue : sellLimitPriceQueue;
        MergeOrder mergeOrder = list.get(tradeOrder.getPrice());
        if (mergeOrder != null) {
            Iterator<TradeOrder> orderIterator = mergeOrder.iterator();
            while (orderIterator.hasNext()) {
                TradeOrder order = orderIterator.next();
                if (order.getId().equalsIgnoreCase(tradeOrder.getId())) {
                    orderIterator.remove();
                    break;
                }
            }
        }
        TradeOrder oldOrder = idTradeOrderMap.remove(tradeOrder.getId());
        // 是否计算盘口
        if (!isCalPlate) {
            return;
        }

        // 撤单 计算剩余成交量 撮合完成订单 计算新老订单成交量
        BigDecimal dealAmount = BigDecimal.ZERO;
        if (tradeOrder.getStatus() == TradeOrderStatusEnum.FILLED.getVal()) {
            dealAmount = tradeOrder.getDealAmount().subtract(oldOrder.getDealAmount());
        } else if (tradeOrder.getStatus() == TradeOrderStatusEnum.CANCEL.getVal()
                || tradeOrder.getStatus() == TradeOrderStatusEnum.CANCEL_EFFECTIVE.getVal()) {
            dealAmount = oldOrder.getTotalAmount().subtract(oldOrder.getDealAmount());
        }
        // 处理盘口信息
        updatePlateQueue(tradeOrder, dealAmount);
    }

    /**
     * 计算bkk手续费
     *
     * @param matchingExchangeInfo
     * @param isDeduct
     * @return
     */
    private MatchingExchangeInfo refreshFeeInfoBasedOnBkkDeduct(MatchingExchangeInfo matchingExchangeInfo, boolean isDeduct) {
        // 1.1 如果不扣减，直接按照原有 获取到的 代币，进行百分比扣减
        if (!isDeduct) {
            return matchingExchangeInfo;
        }
        //1.2 卖出BKK不能使用BKK抵扣功能
        if (!matchingExchangeInfo.isBid() && StringUtils.equals(matchingExchangeInfo.getNormalReduceCoinType(), CoinTypeEnum.BKK.getCode())) {
            return matchingExchangeInfo;
        }
        //2. 检查撮合时候，全局BKK抵扣是否开启的开关
        TradeFeeRateConfigInfo tradeFeeRateConfigInfo = localCacheUtil.getTradeFeeRateConfigInfo();
        boolean bkkDeductSwitchWhenMatch = tradeFeeRateConfigInfo.isBkkDeductSwitchWhenMatch();
        if (!bkkDeductSwitchWhenMatch) {
            return matchingExchangeInfo;
        }
        // 3. 算出来，代币对应手续费 需要多少个BKK，如果不够，则使用正常非抵扣
        BigDecimal deductionDiscount = tradeFeeRateConfigInfo.getBkkDeductDiscount();
        BigDecimal realFeeRateAfterBkkDiscount = matchingExchangeInfo.getFeeRate().multiply(deductionDiscount);
        BigDecimal feeCoinNeeded = matchingExchangeInfo.getNormalGainAmt().multiply(realFeeRateAfterBkkDiscount);
        BigDecimal feeCoinBkkPrice = localCacheUtil.getCoinBkkPrice(matchingExchangeInfo.getNormalGainCoinType(),
                matchingExchangeInfo.getPair(), matchingExchangeInfo.getDealPrice());
        if (NumberUtil.emptyOrZero(feeCoinBkkPrice)) {
            return matchingExchangeInfo;
        }

        // 4. 如果进行BKK抵扣，则将BKK扣减信息缓存起来，但是不会放入到内存模型中
        BigDecimal bkkNeeded = feeCoinNeeded.multiply(feeCoinBkkPrice);
        UserCoinAccount userCoinAccount = exchangeBusiness.getAndLockUserBkkFreeCoinAccountByCache(UserWalletTypeEnum.WALLET.getCode(),
                matchingExchangeInfo.getUserId(),
                CoinTypeEnum.BKK.getCode());
        if (userCoinAccount == null || userCoinAccount.getCoinAmount().compareTo(bkkNeeded) < 0) {
            return matchingExchangeInfo;
        }

        // 4.1 这里进行缓存到上下文
        BigDecimal cachedBkkAmtNotUpdate = userCoinAccount.getCoinAmount().subtract(bkkNeeded);
        userCoinAccount.setCoinAmount(cachedBkkAmtNotUpdate);
        TradeExecutor.updateLocalTmpBkkFreeAccounts(userCoinAccount);

        matchingExchangeInfo.setRealFeeAmt(bkkNeeded);
        matchingExchangeInfo.setRealFeeCoinType(CoinTypeEnum.BKK.getCode());
        return matchingExchangeInfo;
    }

    /**
     * 初始化订单队列
     *
     * @param pair
     */
    private synchronized void initCoinTrader(String pair) {
        Long startTime = System.currentTimeMillis();
        List<TradeOrder> tradeOrders = null;
        try {
            // 清空本地缓存
            cleanMemData();

            // 加载内存队列订单
            tradeOrders = exchangeBusiness.findAllTradingOrderBySymbol(pair);
            if (tradeOrders == null) {
                return;
            }
            for (TradeOrder tradeOrder : tradeOrders) {
                addLimitPriceOrder(tradeOrder);
            }
            ready.set(true);
        } finally {
            error("加载未完成订单到撮合队列:<{}>, 队列大小:<{}>, 耗时:<{}>", pair, (tradeOrders != null ? tradeOrders.size() : 0), (System.currentTimeMillis() - startTime));
        }
    }

    /**
     * 清除交易对队列
     */
    private void cleanMemData() {
        idTradeOrderMap.clear();
        buyLimitPriceQueue.clear();
        sellLimitPriceQueue.clear();
        buyPlateQueue.clear();
        sellPlateQueue.clear();
    }

    /**
     * 检查和冻结余额
     *
     * @param exchangeResult
     * @param focusedOrder
     * @return
     */
    public ExchangeResult commonCheckAvAndFrozeBeforeMatch(ExchangeResult exchangeResult, TradeOrder focusedOrder) {
        // 市价单 不冻结  计划委托下单不冻结  激活订单时冻结  限价单冻结
        if (BizUtil.isMarketOrder(focusedOrder) || BizUtil.isCreateStopLimitOrder(focusedOrder)) {
            return exchangeResult;
        }

        ExchangeAmountInfo exchangeInfo = TradeModelUtil.toExchangeInfo(new TradePair(focusedOrder.getPair()),
                focusedOrder.getDirection(), focusedOrder.getTotalAmount(), focusedOrder.getPrice());
        boolean isMargin = TradeOrderTypeEnum.isMargin(focusedOrder.getWalletType());
        String walletType = isMargin ? UserWalletTypeEnum.MARGIN.getCode() : UserWalletTypeEnum.WALLET.getCode();
        String userId = focusedOrder.getUserId();
        String userRolePrefix = "";
        String remark = BizUtil.remarkForOrder(focusedOrder);
        BalanceChangeRecord balanceChangeRecord = new BalanceChangeRecord.Builder()
                .userId(focusedOrder.getUserId())
                .coinType(exchangeInfo.getReduceCoinType())
                .walletType(walletType).userRole(userRolePrefix + "CREATOR")
                .remark(remark)
                .availableBalanceChange(exchangeInfo.getReduceCoinAmount().negate())
                .frozenBalanceChange(exchangeInfo.getReduceCoinAmount()).build();
        BalanceChangeRequest balanceChangeRequest = new BalanceChangeRequest();
        balanceChangeRequest.setBusinessType(BalanceChangeTypeEnum.CREATE_TRADE.getCode());
        balanceChangeRequest.setBusinessKey(focusedOrder.getId());
        balanceChangeRequest.setBusinessTime(focusedOrder.getCreatedTime());
        balanceChangeRequest.setChangeRecords(Lists.newArrayList(balanceChangeRecord));

        // 检查余额是否足够
        UserCoinAccount userCoinAccount = exchangeBusiness.getAndLockUserCoinAccountByCache(walletType,
                userId, exchangeInfo.getReduceCoinType());
        if (Objects.isNull(userCoinAccount) || userCoinAccount.getCoinAmount().compareTo(exchangeInfo.getReduceCoinAmount()) < 0) {
            exchangeResult.setCode(RESULT_CODE_AV_NOT_ENOUGH);
            exchangeResult.setCodeGlobalParams(Lists.newArrayList(exchangeInfo.getReduceCoinType(), walletType, userId));
        } else {
            exchangeResult.setBalanceChangeReqBeforeMatch(balanceChangeRequest);
        }
        return exchangeResult;
    }

    /**
     * 更新内存订单
     *
     * @param oldTradeOrder
     * @param newTradeOrder
     */
    private void updateTradeOrder(TradeOrder oldTradeOrder, TradeOrder newTradeOrder) {
        if (newTradeOrder != null && oldTradeOrder != null) {
            // 计算单次成交数量
            BigDecimal dealAmount = newTradeOrder.getDealAmount().subtract(oldTradeOrder.getDealAmount());
            // 更新订单信息
            oldTradeOrder.setFeeRate(newTradeOrder.getFeeRate());
            oldTradeOrder.setTotalAnchorCoin(newTradeOrder.getTotalAnchorCoin());
            oldTradeOrder.setDealAmount(newTradeOrder.getDealAmount());
            oldTradeOrder.setTotalAmount(newTradeOrder.getTotalAmount());
            oldTradeOrder.setFrozenAmountByOrder(newTradeOrder.getFrozenAmountByOrder());
            oldTradeOrder.setStatus(newTradeOrder.getStatus());
            oldTradeOrder.setUpdateTime(newTradeOrder.getUpdateTime());

            if(newTradeOrder.isStatusEnd()){
                error("更新内存订单错误，状态为撤销，不应该存在次数据:<{}><{}><{}>", JSON.toJSONString(oldTradeOrder),
                        JSON.toJSONString(newTradeOrder), JSON.toJSONString(TradeExecutor.getLocalExchangeResult()));
            }

            // 更新盘口信息
            updatePlateQueue(newTradeOrder, dealAmount);
        }
    }

    /**
     * 获取当前线程版本号
     *
     * @return
     */
    public Long getThreadVersion() {
        return ++threadVersion;
    }

    /**
     * 添加盘口深度信息
     *
     * @param tradeOrder
     */
    private void addPlateQueue(TradeOrder tradeOrder) {
        if (!isUpdatePlateQueueOrder(tradeOrder)) {
            return;
        }

        // 累加盘口深度
        TreeMap<BigDecimal, BigDecimal> plateQueue = TradeDirectionEnum.isBid(tradeOrder.getDirection()) ?
                buyPlateQueue : sellPlateQueue;
        BigDecimal dealAmount = tradeOrder.getTotalAmount().subtract(tradeOrder.getDealAmount());
        BigDecimal amount = plateQueue.get(tradeOrder.getPrice());
        amount = amount != null ? amount.add(dealAmount) : dealAmount;
        if (amount != null && dealAmount.compareTo(BigDecimal.ZERO) > 0) {
            plateQueue.put(tradeOrder.getPrice(), amount);
        } else {
            plateQueue.remove(tradeOrder.getPrice());
        }
    }

    /**
     * 更新盘口数据
     *
     * @param tradeOrder
     * @param opAmount
     */
    private void updatePlateQueue(TradeOrder tradeOrder, BigDecimal opAmount) {
        if (!isUpdatePlateQueueOrder(tradeOrder)) {
            return;
        }

        // 累加盘口深度
        TreeMap<BigDecimal, BigDecimal> plateQueue = TradeDirectionEnum.isBid(tradeOrder.getDirection()) ?
                buyPlateQueue : sellPlateQueue;
        BigDecimal amount = plateQueue.get(tradeOrder.getPrice());
        if (amount != null) {
            amount = amount.subtract(opAmount);
            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                plateQueue.put(tradeOrder.getPrice(), amount);
            } else {
                plateQueue.remove(tradeOrder.getPrice());
            }
        } else {
            error("跟新盘口数据异常，获取数据为空，请检查代码,opAmount-<{}><{}>", opAmount, JSON.toJSONString(tradeOrder));
        }
    }

    /**
     * 判断订单是否更新盘口
     *
     * @param tradeOrder
     * @return
     */
    private boolean isUpdatePlateQueueOrder(TradeOrder tradeOrder) {
        if (BizUtil.isMarketOrder(tradeOrder)) {
            return false;
        }
        boolean isStopLimitOrder = BizUtil.isStopLimitOrder(tradeOrder);
        if (isStopLimitOrder && (tradeOrder.getStatus() == TradeOrderStatusEnum.STOP_LIMIT_NOT_EFFECTIVE.getVal()
                || tradeOrder.getStatus() == TradeOrderStatusEnum.STOP_LIMIT_ACTIVE_FAILED.getVal())) {
            return false;
        }
        return true;
    }

    public TradePlate getTradePlateQueue() {
        TradePlate tradePlate = new TradePlate();
        tradePlate.setPair(this.pair);
        // 设置买盘口
        int i = 0;
        for (Map.Entry<BigDecimal, BigDecimal> entry : this.buyPlateQueue.entrySet()) {
            if (++i <= 50) {
                tradePlate.getBuyPlateQueue().put(entry.getKey(), entry.getValue());
            }
        }
        // 设置卖盘口
        i = 0;
        for (Map.Entry<BigDecimal, BigDecimal> entry : this.sellPlateQueue.entrySet()) {
            if (++i <= 50) {
                tradePlate.getSellPlateQueue().put(entry.getKey(), entry.getValue());
            }
        }
        return tradePlate;
    }
}