package com.weibi.exchange.model;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class GetPairExchangeStatistics {
    /***交易对信息   pair->[idMapSize=size][bidPriceDepth->size][askPriceDepth->size][bidTotalAmount->sum][askTotalAmount-sum][bidOrderSize=sum][askOrderSize=sum]***/
    private Map<String, Map<String, Object>> pairInfo = new HashMap<>();

}
