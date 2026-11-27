package com.weibi.exchange.listener;


import cn.hutool.core.convert.Convert;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.weibi.config.api.TradePriceService;
import com.weibi.config.enums.CoinTypeEnum;
import com.weibi.config.enums.TradeDealSrcEnum;
import com.weibi.config.enums.TradeOrderStatusEnum;
import com.weibi.config.model.TradePair;
import com.weibi.config.model.trade.TradeDeal;
import com.weibi.config.model.trade.TradeOrder;
import com.weibi.core.balance.delega.model.BalanceChangeRequest;
import com.weibi.core.commons.logging.ILogging;
import com.weibi.exchange.config.KafkaConfig;
import com.weibi.exchange.core.ExchangeBusiness;
import com.weibi.exchange.model.TradeDealSimplify;
import com.weibi.exchange.model.TradeOrderFinished;
import com.weibi.exchange.service.*;
import com.weibi.exchange.util.BizUtil;
import com.weibi.exchange.util.ExchangeKafkaUtil;
import com.weibi.exchange.util.LocalCacheUtil;
import com.weibi.exchange.util.MetricsUtil;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.shardingsphere.api.hint.HintManager;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.weibi.core.commons.api.CommonRespConstant.RESULT_SUCCESS;
import static com.weibi.exchange.util.MetricsUtil.*;

/**
 * 撮合交易下游数据落库
 *
 * @author jsong
 */
@Service
@ConditionalOnProperty(value = "listener.listener_exchange_deal_to_db")
public class ListenerExchangeDealToDb implements ILogging {

    @Autowired
    private UserBalanceChangeHistoryService userBalanceChangeHistoryService;
    @Autowired
    private TradeDealService tradeDealService;
    @Autowired
    private TradeDealSimplifyService tradeDealSimplifyService;
    @Autowired
    private TradePriceService tradePriceService;
    @Autowired
    private LocalCacheUtil localCacheUtil;
    @Autowired
    private ExchangeKafkaUtil exchangeKafkaUtil;
    @Autowired
    private TradeOrderFinishedService tradeOrderFinishedService;
    @Autowired
    private TradeStatisticsFeeService tradeStatisticsFeeService;
    @Resource
    private TransactionTemplate requiredTransactionTemplate;
    @Autowired
    private ExchangeBusiness exchangeBusiness;
    @Autowired
    private MetricsUtil metricsUtil;

    /**
     * 资金代理记录余额变革记录
     *
     * @param records
     */
    @KafkaListener(topics = {KafkaConfig.TOPIC_BALANCE_DELEGA_MANAGE_HISORY, KafkaConfig.TOPIC_BALANCE_RES_INIT_FROM_MATCH_ENGINE},
            containerFactory = KafkaConfig.SYNC_TO_DB_FACTORY_TRANS_MATCH_ENGINE)
    public void onBalanceDelegaEventToDb(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        try {
            HintManager.getInstance().setMasterRouteOnly();
            if (!exchangeKafkaUtil.isProcessMessage(records)) {
                return;
            }
            records.forEach(message -> {
                metricsUtil.timerMetric(WRITE_BALANCE_CHANGE_TO_DB_TPS_RT, () -> {
                    try {
                        BalanceChangeRequest directReq = JSONObject.parseObject(message.value(), BalanceChangeRequest.class);
                        warn("onBalanceDelegaEventToDb接收账单信息:<{}>", JSON.toJSONString(directReq));
                        userBalanceChangeHistoryService.logBalanceRecordsHistory(directReq, RESULT_SUCCESS, String.valueOf(RESULT_SUCCESS));
                    } catch (Exception e) {
                        error("onBalanceDelegaEventToDb写入DB数据库发生错误:<{}>", JSON.toJSONString(message), e);
                    }
                });
            });
        } finally {
            HintManager.clear();
            ack.acknowledge();
        }
    }

    @KafkaListener(topics = {KafkaConfig.TOPIC_DEAL_MADE},
            containerFactory = KafkaConfig.SYNC_TO_DB_SAVE_TRADE_DEAL)
    public void onDealMadeWriteToDb(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        try {
            HintManager.getInstance().setMasterRouteOnly();
            if (!exchangeKafkaUtil.isProcessMessage(records)) {
                return;
            }
            records.forEach(message -> {
                metricsUtil.timerMetric(WRITE_DEAL_TO_DB_TPS_RT, () -> {
                    try {
                        TradeDeal deal = JSON.parseObject(message.value(), TradeDeal.class);
                        // 刷单数据不记录
                        com.weibi.exchange.model.TradeDeal convertTradeDeal = Convert.convert(com.weibi.exchange.model.TradeDeal.class, deal);
                        convertTradeDeal.setCreatedTime(new Timestamp(deal.getCreatedTime().getTime()));
                        warn("onDealMadeWriteToDb写入DB:<{}>", JSON.toJSONString(deal));
                        tradeDealService.insert(convertTradeDeal);
                        if (!deal.getBuyerId().equals(deal.getSellerId())) {
                            TradeDealSimplify tradeDealSimplify = new TradeDealSimplify();
                            BeanUtils.copyProperties(deal, tradeDealSimplify);
                            tradeDealSimplify.setCreatedTime(new Timestamp(deal.getCreatedTime().getTime()));
                            tradeDealSimplify.setDealSrc(BizUtil.isEtp(deal.getPair()) ? TradeDealSrcEnum.ETP.getCode()
                                    : TradeDealSrcEnum.SPOT.getCode());
                            TradePair pair = new TradePair(deal.getPair());
                            tradeDealSimplify.setUsdtPrice(CoinTypeEnum.USDT.getCode().equals(pair.getAnchoredCoin()) ? tradeDealSimplify.getPrice()
                                    : tradePriceService.getCurrentUsdtPriceForCoin(pair.getExchangeCoin()));
                            tradeDealSimplifyService.insert(tradeDealSimplify);
                        }
                    } catch (DuplicateKeyException e) {
                        warn("落库-成交记录写入DB失败:<{}>", JSON.toJSONString(message), e);
                    } catch (Exception e) {
                        error("落库-成交记录写入DB失败:<{}>", JSON.toJSONString(message), e);
                    }
                });
            });
        } finally {
            HintManager.clear();
            ack.acknowledge();
        }
    }

    @KafkaListener(topics = {KafkaConfig.TOPIC_ORDER_FILLED, KafkaConfig.TOPIC_ORDER_CANCELED, KafkaConfig.TOPIC_ORDER_FAILED},
            containerFactory = KafkaConfig.SYNC_TO_DB_FACTORY_SAVE_FINISHED_ORDER)
    public void writeToDbOnOrderFinished(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        try {
            HintManager.getInstance().setMasterRouteOnly();
            if (!exchangeKafkaUtil.isProcessMessage(records)) {
                return;
            }
            records.forEach(message -> {
                metricsUtil.timerMetric(WRITE_FINISHED_ORDER_TO_DB_TPS_RT, () -> {
                    try {
                        TradeOrder order = JSON.parseObject(message.value(), TradeOrder.class);
                        if (!localCacheUtil.getPlatFormUser().contains(order.getUserId()) || order.getStatus() == TradeOrderStatusEnum.FILLED.getVal()) {
                            TradeOrderFinished entity = new TradeOrderFinished();
                            BeanUtils.copyProperties(order, entity);
                            if (Objects.isNull(entity.getPrice())) {
                                entity.setPrice(BigDecimal.ZERO);
                            }
                            warn("writeToDbOnOrderFinished写入DB：<{}>", JSON.toJSONString(order));
                            tradeOrderFinishedService.insert(entity);
                        }
                    } catch (Exception e) {
                        error("插入finished order db到数据库发生错误:<{}>", JSON.toJSONString(message), e);
                    }
                });
            });
        } finally {
            HintManager.clear();
            ack.acknowledge();
        }
    }

    @KafkaListener(topics = KafkaConfig.TOPIC_DEAL_MADE,
            containerFactory = KafkaConfig.SYNC_TO_DB_FACTORY_DEAL_MINING)
    public void miningOnDealMade(List<ConsumerRecord<?, String>> records, Acknowledgment ack) {
        AtomicBoolean encounteredException = new AtomicBoolean(false);
        for (ConsumerRecord<?, String> record : records) {
            String dealMessage = record.value();
            try {
                TradeDeal deal = JSONObject.parseObject(dealMessage, TradeDeal.class);
                tradeStatisticsFeeService.createFeeRecordByDeal(deal);
            } catch (Exception e) {
                // 由于遇到幂等情况，异常会被捕获，所以这里的异常会被捕获
                encounteredException.getAndSet(true);
                error("根据成交数据插入手续费记录:发生未知异常，请立即检查,dealMessage:{}", dealMessage, e);
                break;
            }
        }
        if (!encounteredException.get()) {
            ack.acknowledge();
        } else {
            warn("批量处理成交信息任务，遇到异常，不会进行offset 确认");
        }
    }

    /**
     * 平台手续费计算扣除
     *
     * @param records
     */
    @KafkaListener(topics = {KafkaConfig.TOPIC_EXCHANGE_PUBLIC_FEE},
            containerFactory = KafkaConfig.SYNC_TO_DB_FACTORY_PUBLIC_FEE)
    public void onPublicBalanceEventToDb(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        try {
            synchronized (this) {
                List<BalanceChangeRequest> balanceChangeRequests = new ArrayList<>();
                records.forEach(message -> {
                    if (!localCacheUtil.publicFeeIsProc(message.partition(), message.offset(), false)) {
                        BalanceChangeRequest directReq = JSONObject.parseObject(message.value(), BalanceChangeRequest.class);
                        balanceChangeRequests.add(directReq);
                    }
                });
                try {
                    HintManager.getInstance().setMasterRouteOnly();
                    // 订单撮合 和 清算  批量事务提交
                    requiredTransactionTemplate.execute(new TransactionCallbackWithoutResult() {
                        @Override
                        protected void doInTransactionWithoutResult(TransactionStatus status) {
                            // 扣除平台手续费
                            boolean result = exchangeBusiness.clearingPublicFeeBalanceChangeRequest(balanceChangeRequests);
                            // 发送kafka余额消息
                            if (result) {
                                for (BalanceChangeRequest balanceChangeRequest : balanceChangeRequests) {
                                    exchangeKafkaUtil.sendTransResultInitFromMatchEngine(balanceChangeRequest);
                                }
                            }
                            // 记录消息已处理
                            records.forEach(message -> {
                                localCacheUtil.publicFeeIsProc(message.partition(), message.offset(), true);
                            });
                            ack.acknowledge();
                        }
                    });
                } finally {
                    HintManager.clear();
                }

            }
        } catch (Exception e) {
            error("增加平台手续费错误:", e);
        }
    }
}
