package com.weibi.exchange.model;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class OrdersDepthVo implements Serializable {
    /**
     * 卖单深度
     */
    private List<OrdersDepthItemVo> askDepth = new ArrayList<>();
    /**
     * 买单深度
     */
    private List<OrdersDepthItemVo> bidDepth = new ArrayList<>();
}
