package com.weibi.exchange.listener;

import com.alibaba.fastjson.JSON;
import com.weibi.config.api.TradePriceService;
import com.weibi.config.model.trade.TradeDeal;
import com.weibi.config.model.trade.TradeOrder;
import com.weibi.core.commons.logging.ILogging;
import com.weibi.exchange.api.ExchangeService;
import com.weibi.exchange.config.KafkaConfig;
import com.weibi.exchange.core.TradeExecutor;
import com.weibi.exchange.util.BizUtil;
import com.weibi.exchange.util.ExchangeKafkaUtil;
import com.weibi.exchange.util.LocalCacheUtil;
import com.weibi.exchange.util.MetricsUtil;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.weibi.exchange.util.MetricsUtil.ALL_PROCESS_ORDER_TPS_RT;

/**
 * 撮合kafka监听
 *
 * @author jsong
 */
@Component
@ConditionalOnProperty(value = "listener.listener_exchange_order")
public class ListenerExchangeOrder implements ILogging {

    @Autowired
    private LocalCacheUtil localCacheUtil;
    @Autowired
    private MetricsUtil metricsUtil;
    @Autowired
    private TradeExecutor tradeExecutor;
    @Autowired
    private ExchangeService exchangeService;
    @Autowired
    private TradePriceService tradePriceService;
    @Autowired
    private ExchangeKafkaUtil exchangeKafkaUtil;
    @Value("${exchange.listenerExchangeOrderDiscardFlag:false}")
    private String listenerExchangeOrderDiscardFlag;

    @KafkaListener(topics = KafkaConfig.TOPIC_EXCHANGE_ORDER,
            containerFactory = KafkaConfig.EXCHANGE_ORDER_FACTORY)
    public void onExchangeOrder(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        try {
            if (!exchangeKafkaUtil.isProcessMessage(records)) {
                return;
            }
            metricsUtil.timerMetric(ALL_PROCESS_ORDER_TPS_RT, () -> {
                tradeExecutor.processOrder(BizUtil.convertKafkaMsgToTradeOrderCmd(records));
            });
        } catch (Exception e) {
            error("onExchangeOrder撮合监听器处理失败:<{}>", JSON.toJSONString(records), e);
        } finally {
            ack.acknowledge();
        }
    }

    @KafkaListener(topics = KafkaConfig.TOPIC_EXCHANGE_ORDER_HOT,
            containerFactory = KafkaConfig.EXCHANGE_ORDER_HOT_FACTORY)
    public void onExchangeOrderHot(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        try {
            if (!exchangeKafkaUtil.isProcessMessage(records)) {
                return;
            }
            metricsUtil.timerMetric(ALL_PROCESS_ORDER_TPS_RT, () -> {
                tradeExecutor.processOrder(BizUtil.convertKafkaMsgToTradeOrderCmd(records));
            });
        } catch (Exception e) {
            error("onExchangeOrder撮合监听器处理失败:<{}>", JSON.toJSONString(records), e);
        } finally {
            ack.acknowledge();
        }
    }

    @KafkaListener(topics = {KafkaConfig.TOPIC_DEAL_MADE, "deal_made", "general_deal"},
            containerFactory = KafkaConfig.ACTIVE_STOP_LIMIT_BY_DEAL_FACTORY)
    public void onDealMade(String message) {
        TradeDeal deal = JSON.parseObject(message, TradeDeal.class);

        // 激活订单
        try {
            List<TradeOrder> orders = localCacheUtil.findByStopLimitPairPriceRange(deal.getPair(), deal.getPrice());
            for (TradeOrder order : orders) {
                try {
                    exchangeService.activeStopLimitOrder(order);
                } catch (Exception e) {
                    error("触发计划委托处理成交记录失败，单条订单:<{}>", JSON.toJSONString(order), e);
                }
            }
        } catch (Exception e) {
            error("触发计划委托处理成交记录失败:<{}>", message, e);
        }
    }

}
