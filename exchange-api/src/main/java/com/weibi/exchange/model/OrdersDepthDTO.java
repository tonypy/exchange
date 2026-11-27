package com.weibi.exchange.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 盘口深度dto
 *
 * @author jsong
 */
@Data
public class OrdersDepthDTO {
    /**
     * 交易对
     */
    private String pair;
    /**
     * 交易方向
     */
    private String direction;
    /**
     * 价格
     */
    private BigDecimal price;
    /**
     * 交易数量
     */
    private BigDecimal exchangeAmount;
}
