package com.weibi.exchange.listener;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.weibi.config.enums.TradeOrderSrcEnum;
import com.weibi.config.model.trade.TradeDeal;
import com.weibi.config.model.trade.TradeOrder;
import com.weibi.core.balance.delega.model.BalanceChangeRequest;
import com.weibi.core.commons.logging.ILogging;
import com.weibi.core.datacenter.model.EsFinishedOrderModel;
import com.weibi.core.datacenter.model.EsTradeDealModel;
import com.weibi.core.datacenter.model.EsUserBalanceHistoryModel;
import com.weibi.core.datacenter.service.WriteToEsService;
import com.weibi.exchange.config.KafkaConfig;
import com.weibi.exchange.util.BizUtil;
import com.weibi.exchange.util.ExchangeKafkaUtil;
import com.weibi.exchange.util.LocalCacheUtil;
import com.weibi.exchange.util.MetricsUtil;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.weibi.exchange.core.ModelConvertor.*;
import static com.weibi.exchange.util.MetricsUtil.*;

/**
 * 撮合交易下游数据落库
 *
 * @author jsong
 */
@Service
@ConditionalOnProperty(value = "listener.listener_exchange_deal_to_es")
public class ListenerExchangeDealToEs implements ILogging {

    @Autowired
    private WriteToEsService writeToEsService;
    @Autowired
    private LocalCacheUtil localCacheUtil;
    @Autowired
    private MetricsUtil metricsUtil;
    @Autowired
    private ExchangeKafkaUtil exchangeKafkaUtil;

    @KafkaListener(topics = {KafkaConfig.TOPIC_ORDER_FILLED, KafkaConfig.TOPIC_ORDER_CANCELED, KafkaConfig.TOPIC_ORDER_FAILED},
            containerFactory = KafkaConfig.SYNC_TO_DATACENTER_FACTORY)
    public void onOrderFinishedWriteToEs(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        List<EsFinishedOrderModel> orders = new ArrayList<>();
        try {
            if (!exchangeKafkaUtil.isProcessMessage(records)) {
                return;
            }
            metricsUtil.timerMetric(WRITE_FINISHED_ORDER_TO_ES_TPS_RT, () -> {
                List<String> platformUsers = localCacheUtil.getPlatFormUser();
                List<String> usersNeedTradeInfo = localCacheUtil.getUsersNeedTradeInfo();
                // 如果不是平台用户， 或者在 需要账单的用户上
                records.stream()
                        .map(record -> {
                            TradeOrder tradeOrder = JSON.parseObject(record.value(), TradeOrder.class);
                            return fromFinishedOrderToEsModel(tradeOrder);
                        })
                        .filter(order -> !platformUsers.contains(order.getUserId()) || usersNeedTradeInfo.contains(order.getUserId()))
                        .collect(Collectors.toList())
                        .forEach(item -> orders.add(item));

                // 重新获取挂单数据：市值账户，通过OPENAPI下单方式，完全成交或部分成交并撤销的挂单
                try {
                    // 用于排重
                    Set<String> orderIds = orders.stream().map(EsFinishedOrderModel::getId).collect(Collectors.toSet());
                    List<EsFinishedOrderModel> filledOrders = records.stream().map(record -> {
                        TradeOrder tradeOrder = JSON.parseObject(record.value(), TradeOrder.class);
                        return fromFinishedOrderToEsModel(tradeOrder);
                    }).filter(order -> platformUsers.contains(order.getUserId())
                            && TradeOrderSrcEnum.isFromOpenApi(order.getSrc())
                            && !orderIds.contains(order.getId())
                            && order.getDealAmount().compareTo(BigDecimal.ZERO) > 0
                            && BizUtil.filterUselessPair(order.getPair()))
                            .collect(Collectors.toList());
                    if (filledOrders.size() > 0) {
                        warn("print platform users finish order,records: <{}> filledOrders: <{}>", records, filledOrders);
                        orders.addAll(filledOrders);
                    }
                } catch (Exception e) {
                    error("Failed to obtain the transaction pending order of platform users in the white list：{}", e.getMessage());
                }
                warn("onOrderFinishedWriteToEs写入es：<{}>", JSON.toJSONString(orders));
                writeToEsService.writeFinishedOrderToEs(orders);
            });
        } catch (Exception e) {
            error("插入es失败:<{}>", JSON.toJSONString(orders), e);
        } finally {
            ack.acknowledge();
        }
    }

    @KafkaListener(topics = {KafkaConfig.TOPIC_DEAL_MADE, KafkaConfig.TOPIC_GENERAL_DEAL},
            containerFactory = KafkaConfig.SYNC_TO_DATACENTER_FACTORY)
    public void onTradeDealToEs(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        List<EsTradeDealModel> dealModels = new ArrayList<>();
        try {
            if (!exchangeKafkaUtil.isProcessMessage(records)) {
                return;
            }
            metricsUtil.timerMetric(WRITE_DEAL_TO_ES_TPS_RT, () -> {
                records.forEach(message -> {
                    TradeDeal deal = JSONObject.parseObject(message.value(), TradeDeal.class);
                    dealModels.add(fromDealToEsModels(deal));
                });
                warn("onTradeDealToEs写入es：<{}>", JSON.toJSONString(dealModels));
                writeToEsService.writeDealModelsToEs(dealModels);
            });
        } catch (Exception e) {
            error("插入es失败:<{}>", JSON.toJSONString(dealModels), e);
        } finally {
            ack.acknowledge();
        }
    }

    @KafkaListener(topics = {KafkaConfig.TOPIC_SUCC_BALANCE_HIST_WRITTEN, KafkaConfig.TOPIC_BALANCE_RES_INIT_FROM_MATCH_ENGINE},
            containerFactory = KafkaConfig.SYNC_TO_DATACENTER_FACTORY)
    public void onUserBalanceHistMadeWriteToEs(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        List<EsUserBalanceHistoryModel> esModels = new ArrayList<>();
        try {
            if (!exchangeKafkaUtil.isProcessMessage(records)) {
                return;
            }
            metricsUtil.timerMetric(WRITE_BALANCE_CHANGE_TO_ES_TPS_RT, () -> {
                List<String> platformUsers = localCacheUtil.getPlatFormUser();
                List<String> usersNeedTradeInfo = localCacheUtil.getUsersNeedTradeInfo();
                records.forEach(record -> {
                    BalanceChangeRequest directReq = JSONObject.parseObject(record.value(), BalanceChangeRequest.class);
                    esModels.addAll(fromReqToEsModels(directReq));
                });
                warn("onUserBalanceHistMadeWriteToEs写入es：<{}>", JSON.toJSONString(esModels));
                writeToEsService.writeHistModelsToEs(
                        esModels.stream()
                                .filter(model -> !platformUsers.contains(model.getUserId()) || usersNeedTradeInfo.contains(model.getUserId()))
                                .collect(Collectors.toList())
                );
            });
        } catch (Exception e) {
            error("插入es失败:<{}>", JSON.toJSONString(esModels), e);
        } finally {
            ack.acknowledge();
        }
    }
}
