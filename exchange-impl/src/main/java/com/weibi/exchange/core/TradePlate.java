package com.weibi.exchange.core;


import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.TreeMap;

/**
 * 交易对盘口深度
 */
@Data
public class TradePlate implements Serializable {
    private String pair;
    /***买房盘口队列  price->amount 降序***/
    private TreeMap<BigDecimal, BigDecimal> buyPlateQueue = new TreeMap<>(Comparator.reverseOrder());
    /***卖方盘口队列 升序***/
    private TreeMap<BigDecimal, BigDecimal> sellPlateQueue = new TreeMap<>(Comparator.naturalOrder());
}
