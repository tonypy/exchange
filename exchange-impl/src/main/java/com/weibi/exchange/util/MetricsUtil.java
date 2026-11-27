package com.weibi.exchange.util;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricRegistryListener.Base;
import com.codahale.metrics.Slf4jReporter;
import com.codahale.metrics.Timer;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 统计工具类
 * 1、
 *
 * @author jsong
 */
@Component
public class MetricsUtil {
    /***API下单QPS***/
    public static final String PROCESS_API_CREATE_ORDER_TPS_RT = "TIMER_PROCESS_API_CREATE_ORDER.TPS.RT";
    /***API 撤单QPS***/
    public static final String PROCESS_API_CANCEL_ORDER_TPS_RT = "TIMER_PROCESS_API_CANCEL_ORDER.TPS.RT";
    /***API下单错误QPS***/
    public static final String PROCESS_API_CREATE_ORDER_ERROR_TPS_RT = "TIMER_PROCESS_API_CREATE_ORDER_ERROR.TPS.RT";
    /***API 撤单错误QPS***/
    public static final String PROCESS_API_CANCEL_ORDER_ERROR_TPS_RT = "TIMER_PROCESS_API_CANCEL_ORDER_ERROR.TPS.RT";
    /***撮合 一批处理QPS***/
    public static final String ALL_PROCESS_ORDER_TPS_RT = "TIMER_ALL_PROCESS_ORDER.TPS.RT";
    /***撮合 单个订单处理QPS***/
    public static final String SINGLE_PROCESS_ORDER_TPS_RT = "TIMER_SINGLE__PROCESS_ORDER.TPS.RT";
    /***账单落库DB qps***/
    public static final String WRITE_BALANCE_CHANGE_TO_DB_TPS_RT = "TIMER_WRITE_BALANCE_CHANGE_TO_DB.TPS.RT";
    /***成交记录落库DB qps***/
    public static final String WRITE_DEAL_TO_DB_TPS_RT = "TIMER_WRITE_DEAL_TO_DB.TPS.RT";
    /***完成订单落库DB qps***/
    public static final String WRITE_FINISHED_ORDER_TO_DB_TPS_RT = "TIMER_WRITE_FINISHED_ORDER_TO_DB.TPS.RT";
    /***账单落库ES qps***/
    public static final String WRITE_BALANCE_CHANGE_TO_ES_TPS_RT = "TIMER_WRITE_BALANCE_CHANGE_TO_ES.TPS.RT";
    /***成交记录落库ES qps***/
    public static final String WRITE_DEAL_TO_ES_TPS_RT = "TIMER_WRITE_DEAL_TO_ES.TPS.RT";
    /***完成订单落库ES qps***/
    public static final String WRITE_FINISHED_ORDER_TO_ES_TPS_RT = "TIMER_WRITE_FINISHED_ORDER_TO_ES.TPS.RT";
    /***通用kafka 发送消息 qps-rt***/
    public static final String COMMON_KAFKA_SEND_TPS_RT = "COMMON_KAFKA_SEND.TPS.RT";
    /***下单 kafka 发送消息 qps-rt***/
    public static final String DANGER_KAFKA_SEND_TPS_RT = "DANGER_KAFKA_SEND.TPS.RT";

    private MetricRegistry metricRegistry = new MetricRegistry();
    private Slf4jReporter reporter = Slf4jReporter.forRegistry(metricRegistry)
            .convertRatesTo(TimeUnit.SECONDS)
            .convertDurationsTo(TimeUnit.MILLISECONDS)
            .withLoggingLevel(Slf4jReporter.LoggingLevel.WARN)
            .build();
    private ConcurrentHashMap<String, LinkedList<String>> histogramSlidWindowMap = new ConcurrentHashMap<>();
    private boolean openMetric = true;

    @PostConstruct
    public void init() {
        reporter.start(30, TimeUnit.SECONDS);
        // 有新的指标加入 删除之前的指标
        metricRegistry.addListener(new Base() {
            @Override
            public void onHistogramAdded(String name, Histogram histogram) {
                if (name.startsWith("HISTOGRAM_SLIDWINDOW_")) {
                    String[] split = name.split(":");
                    histogramSlidWindowMap.putIfAbsent(split[0], new LinkedList<>());
                    LinkedList<String> linkedList = histogramSlidWindowMap.get(split[0]);
                    linkedList.addFirst(name);
                    while (linkedList.size() > 1) {
                        String removeLast = linkedList.removeLast();
                        metricRegistry.remove(removeLast);
                    }
                }
            }
        });
    }

    public void processOrderTimerMetric(String metricName, Runnable runnable) {
        Timer.Context context = getTimer(ALL_PROCESS_ORDER_TPS_RT).time();
        try {
            timerMetric(metricName, runnable);
        } finally {
            stopTimer(context);
        }
    }

    public void timerMetric(String metricName, Runnable runnable) {
        Timer.Context context = getTimer(metricName).time();
        try {
            runnable.run();
        } finally {
            stopTimer(context);
        }
    }

    public <T> T timerMetricByRes(String metricName, Supplier<T> supplier) {
        Timer.Context context = getTimer(metricName).time();
        try {
            return supplier.get();
        } finally {
            stopTimer(context);
        }
    }

    public void histogramMetric(String metricName, long timestamp) {
        Histogram histogram = getHistogram(metricName);
        if (histogram != null) {
            histogram.update(System.currentTimeMillis() - timestamp);
        }
    }

    private Histogram getHistogram(String metricName) {
        return openMetric ? metricRegistry.histogram(metricName + ":" + getMinute()) : null;
    }

    private Long getMinute() {
        return System.currentTimeMillis() / 60000;
    }

    private Timer getTimer(String metricName) {
        return openMetric ? metricRegistry.timer(metricName) : null;
    }

    private void stopTimer(Timer.Context context) {
        if (context != null) {
            context.stop();
        }
    }
}
