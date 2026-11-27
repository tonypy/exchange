package com.weibi.exchange.model;

import lombok.Data;

import java.util.Map;

@Data
public class GetExchangeMemStatistics {
    /***本地缓存统计***/
    private Map<String, Object> localCacheStatistics;
    /***kafka工具类***/
    private Map<String, Object> kafkaUtilStatistics;
}
