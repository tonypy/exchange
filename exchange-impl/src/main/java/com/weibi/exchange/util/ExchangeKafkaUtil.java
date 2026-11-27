package com.weibi.exchange.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.weibi.config.enums.TradeOrderStatusEnum;
import com.weibi.config.model.trade.TradeDeal;
import com.weibi.config.model.trade.TradeOrder;
import com.weibi.core.balance.delega.enums.BalanceChangeTypeEnum;
import com.weibi.core.balance.delega.model.BalanceChangeRecord;
import com.weibi.core.balance.delega.model.BalanceChangeRequest;
import com.weibi.core.balance.delega.utils.UserAccountsUtils;
import com.weibi.core.commons.logging.ILogging;
import com.weibi.exchange.config.KafkaConfig;
import com.weibi.exchange.core.CoinTrader;
import com.weibi.exchange.core.ProcessOrderTypeEnum;
import com.weibi.exchange.core.TradeOrderCmd;
import com.weibi.exchange.core.TradePlate;
import com.weibi.exchange.model.ExchangeResult;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.weibi.exchange.util.MetricsUtil.COMMON_KAFKA_SEND_TPS_RT;
import static com.weibi.exchange.util.MetricsUtil.DANGER_KAFKA_SEND_TPS_RT;

/**
 * 发送kafka消息
 *
 * @author jsong
 */
@Service
public class ExchangeKafkaUtil implements ILogging {
    /***币种对应topic配置***/
    private static ConcurrentHashMap<String, String> PAIR_EXCHANGE_TOPIC_MAP = new ConcurrentHashMap<>();
    /***发送kafka消息线程池***/
    private ThreadPoolExecutor executorService = new ThreadPoolExecutor(10, 10, 10, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    @Resource
    private KafkaTemplate<String, String> kafkaTemplate;
    @Resource
    private KafkaTemplate<String, String> dangerKafkaTemplate;
    @Autowired
    private LocalCacheUtil localCacheUtil;
    @Autowired
    private MetricsUtil metricsUtil;
    @Value("${exchange.listenerExchangeOrderDiscardFlag:false}")
    private String listenerExchangeOrderDiscardFlag;

    /**
     * 发送kafka撮合信息
     *
     * @param tradeOrderCmd
     * @return
     */
    public boolean sendMsgForApiExchangeOrder(TradeOrderCmd tradeOrderCmd) {
        TradeOrder tradeOrder = tradeOrderCmd.getTradeOrder();
        // 根据币对进行topic路由
        String topic = getExchageTopicByPair(tradeOrder.getPair());
        // 同步发送撮合订单 委托单创建不进入撮合 触发价格时扣费进入撮合
        if (StringUtils.isNotEmpty(topic)) {
            doDangerKafkaSendMsg(topic, JSON.toJSONString(tradeOrderCmd), tradeOrder.getPair());
            return true;
        } else {
            return false;
        }
    }

    /**
     * 撮合后发送撮合消息  成交记录、完成订单、余额变更
     *
     * @param exchangeResult
     */
    public void sendMsgForExchange(ExchangeResult exchangeResult) {
        executorService.submit(() -> {
            if (exchangeResult.getBalanceChangeReqBeforeMatch() != null) {
                // 发送冻结余额信息
                BalanceChangeRequest changeRequest = exchangeResult.getBalanceChangeReqBeforeMatch();
                //BizUtil.remarkBalanceChangeRequest(exchangeResult.getTraceId(), changeRequest);
                changeRequest.setChangeRecords(UserAccountsUtils.calBalanceChangeLogsAndUpdateHistLogs(
                        changeRequest.getChangeRecords(), exchangeResult.getBalanceChangeHist()));
                sendTransResultInitFromMatchEngine(changeRequest);
            }
            exchangeResult.getMatchingResults().forEach(matchingResult -> {
                // 发送余额信息
                BalanceChangeRequest changeRequest = matchingResult.getMatchBalanceChangeRequest();
                //BizUtil.remarkBalanceChangeRequest(exchangeResult.getTraceId(), changeRequest);
                changeRequest.setChangeRecords(UserAccountsUtils.calBalanceChangeLogsAndUpdateHistLogs(
                        changeRequest.getChangeRecords(), exchangeResult.getBalanceChangeHist()));
                sendTransResultInitFromMatchEngine(changeRequest);
                // 发送交易消息
                TradeDeal tradeDeal = matchingResult.getTradeDeal();
                if (tradeDeal != null) {
                    localCacheUtil.updatePairPrice(tradeDeal.getPair(), tradeDeal.getPrice());
                    sendDealMade(tradeDeal);
                }
                // 发送平台手续费记录消息 进行一部计算
                List<BalanceChangeRecord> publicRealFeeRecords = matchingResult.getPublicRealFeeRecords();
                if (publicRealFeeRecords != null
                        && !publicRealFeeRecords.isEmpty()) {
                    changeRequest.setChangeRecords(publicRealFeeRecords);
                    sendPublicFeeMessage(changeRequest);
                }
            });
            // 发送完成订单
            exchangeResult.getDataOrderDeleted().forEach(item -> {
                if (item.getStatus() == TradeOrderStatusEnum.FILLED.getVal()) {
                    sendOrderFilled(item);
                } else {
                    sendOrderCanceled(item);
                }
            });
            // 发送下单
            exchangeResult.getBizOrderAdded().forEach(item -> {
                sendOrderAddedToQueue(item);
            });
        });
    }

    /**
     * 系统处理订单错误 用户下单和撤单失败 进行一次补偿
     *
     * @param exchangeResult
     */
    public void sendExchangeSystemFailRetry(ExchangeResult exchangeResult) {
        executorService.submit(() -> {
            TradeOrder tradeOrder = exchangeResult.getOldFocusedOrder();
            if (tradeOrder != null && BizUtil.isUserSrcOrder(tradeOrder)) {
                TradeOrderCmd tradeOrderCmd = new TradeOrderCmd();
                tradeOrderCmd.setTradeOrder(tradeOrder);
                if (BalanceChangeTypeEnum.CREATE_TRADE.getCode().equalsIgnoreCase(exchangeResult.getAction())) {
                    tradeOrderCmd.setAction(ProcessOrderTypeEnum.CREATE_ORDER.getCode());
                } else if (BalanceChangeTypeEnum.CANCEL_TRADE.getCode().equalsIgnoreCase(exchangeResult.getAction())) {
                    tradeOrderCmd.setAction(ProcessOrderTypeEnum.CANCEL_ORDER.getCode());
                }
                boolean result = sendMsgForApiExchangeOrder(tradeOrderCmd);
                if (!result) {
                    error("变更撮合topic配置， 拒绝发送撮合一定时间如果需要进行手动补偿:<{}>", JSON.toJSONString(tradeOrderCmd));
                }
            }
        });
    }

    /**
     * 发送kafka账单记录
     *
     * @param balanceChangeRequest
     */
    public void sendTransResultInitFromMatchEngine(BalanceChangeRequest balanceChangeRequest) {
        doSendMsg(KafkaConfig.TOPIC_BALANCE_RES_INIT_FROM_MATCH_ENGINE, JSONObject.toJSONString(balanceChangeRequest));
    }

    public Map<String, Object> getKafkaUtilStatistics() {
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("getQueue", executorService.getQueue().size());
        resultMap.put("getActiveCount", executorService.getActiveCount());
        resultMap.put("getTaskCount", executorService.getTaskCount());
        resultMap.put("getCompletedTaskCount", executorService.getCompletedTaskCount());
        return resultMap;
    }

    private void sendDealMade(TradeDeal deal) {
        doSendMsg(KafkaConfig.TOPIC_DEAL_MADE, JSONObject.toJSONString(deal), deal.getPair());
    }

    private void sendOrderFilled(TradeOrder tradeOrder) {
        doSendMsg(KafkaConfig.TOPIC_ORDER_FILLED, JSONObject.toJSONString(tradeOrder), tradeOrder.getPair());
    }

    private void sendOrderCanceled(TradeOrder tradeOrder) {
        doSendMsg(KafkaConfig.TOPIC_ORDER_CANCELED, JSONObject.toJSONString(tradeOrder), tradeOrder.getPair());
    }

    private void sendOrderFailed(TradeOrder tradeOrder) {
        doSendMsg(KafkaConfig.TOPIC_ORDER_FAILED, JSONObject.toJSONString(tradeOrder), tradeOrder.getPair());
    }

    private void sendOrderAddedToQueue(TradeOrder arrivedOrder) {
        doSendMsg(KafkaConfig.TOPIC_ORDER_ADDED_TO_QUEUE, JSONObject.toJSONString(arrivedOrder), arrivedOrder.getPair());
    }

    private void sendPublicFeeMessage(BalanceChangeRequest balanceChangeRequest) {
        doSendMsg(KafkaConfig.TOPIC_EXCHANGE_PUBLIC_FEE, JSONObject.toJSONString(balanceChangeRequest));
    }

    private void doSendMsg(String topic, String content, String... key) {
        warn("发送kafka消息:topic:" + topic + ";content:" + content + ";key:" + key);
        try {
            metricsUtil.timerMetric(COMMON_KAFKA_SEND_TPS_RT, () -> {
                if (key != null && key.length > 0) {
                    kafkaTemplate.send(topic, key[0], content);
                } else {
                    kafkaTemplate.send(topic, content);
                }
            });
        } catch (Exception e) {
            error("发送kafka失败, 需要手动补偿:<{}>:<{}>", topic, content, e);
        }
    }

    private void doDangerKafkaSendMsg(String topic, String content, String... key) {
        BizUtil.BIZ_WARN_LOG.warn("发送doDangerKafkaSendMsg消息:topic:" + topic + ";content:" + content + ";key:" + key);
        try {
            metricsUtil.timerMetric(DANGER_KAFKA_SEND_TPS_RT, () -> {
                if (key != null && key.length > 0) {
                    dangerKafkaTemplate.send(topic, key[0], content);
                } else {
                    dangerKafkaTemplate.send(topic, content);
                }
            });
        } catch (Exception e) {
            error("发送doDangerKafkaSendMsg失败:<{}>:<{}>", topic, content, e);
        }
    }

    /**
     * 根据币对获取撮合topic 根据数据库配置等级 分为热币和常规币种 etp单独  如果获取失败 前端报错
     *
     * @param pair
     * @return
     */
    private String getExchageTopicByPair(String pair) {
        String topic = "";
        // 如果币对不在拒绝队列 则 获取真实交易对
        if (!localCacheUtil.getExchangePairRefuse(pair)) {
            Integer level = localCacheUtil.getExchangePairLevel(pair);
            topic = level == null || level > 1 ? KafkaConfig.TOPIC_EXCHANGE_ORDER : KafkaConfig.TOPIC_EXCHANGE_ORDER_HOT;
            // 如果当前交易对为空 或者 币种配置发生变化，刷洗你内存配置 同时拒绝 请求30秒
            String pairTopic = PAIR_EXCHANGE_TOPIC_MAP.get(pair);
            if (pairTopic == null || !pairTopic.equalsIgnoreCase(topic)) {
                synchronized (this) {
                    BizUtil.BIZ_WARN_LOG.warn("设置本地缓存币对对应topic：pair-<{}>o-tp<{}>n-tp<{}>is-ref<{}>", pair, pairTopic, topic, pairTopic != null);
                    if (pairTopic != null) {
                        localCacheUtil.setExchangePairRefuse(pair, true);
                    }
                    PAIR_EXCHANGE_TOPIC_MAP.put(pair, topic);
                }
            }
        }
        return topic;
    }

    /**
     * 发送交易对盘口信息
     *
     * @param coinTrader
     */
    public void sendPlateQueue(CoinTrader coinTrader) {
        executorService.submit(() -> {
            TradePlate tradePlate = coinTrader.getTradePlateQueue();
            doDangerKafkaSendMsg(KafkaConfig.TOPIC_EXCHANGE_PLATE_QUEUE, JSON.toJSONString(tradePlate), tradePlate.getPair());
        });
    }

    /**
     * 是否处理消息
     *
     * @param records
     * @return
     */
    public boolean isProcessMessage(List<ConsumerRecord<String, String>> records) {
        if ("true".equalsIgnoreCase(listenerExchangeOrderDiscardFlag)) {
            warn("丢弃卡kafka数据:<{}>", JSON.toJSONString(records));
            return false;
        }
        return true;
    }
}
