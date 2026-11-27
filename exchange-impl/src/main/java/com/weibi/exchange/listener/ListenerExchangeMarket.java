package com.weibi.exchange.listener;

import com.alibaba.fastjson.JSON;
import com.weibi.core.commons.logging.ILogging;
import com.weibi.exchange.config.KafkaConfig;
import com.weibi.exchange.core.TradePlate;
import com.weibi.exchange.util.LocalCacheUtil;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 盘口数据监听
 *
 * @author jsong
 */
@Component
@ConditionalOnProperty(value = "listener.listener_exchange_market")
public class ListenerExchangeMarket implements ILogging {

    @Autowired
    private LocalCacheUtil localCacheUtil;

    @KafkaListener(topics = KafkaConfig.TOPIC_EXCHANGE_PLATE_QUEUE,
            containerFactory = KafkaConfig.EXCHANGE_PLATE_QUEUE_FACTORY)
    public void onExchangePlateQueue(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        try {
            for (ConsumerRecord<String, String> row : records) {
                TradePlate tradePlate = JSON.parseObject(row.value(), TradePlate.class);
                localCacheUtil.setTradePlate(tradePlate.getPair(), tradePlate);
            }
        } catch (Exception e) {
            error("onExchangePlateQueue撮合盘口监听器处理失败:<{}>", JSON.toJSONString(records), e);
        } finally {
            ack.acknowledge();
        }
    }
}
