package com.weibi.exchange.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrdersDepthItemVo implements Serializable {
    /**
     * 价格
     */
    private BigDecimal price;
    /**
     * 交易量
     */
    private BigDecimal exchangeAmount;
}
