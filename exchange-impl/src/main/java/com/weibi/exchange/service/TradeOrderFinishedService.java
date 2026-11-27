package com.weibi.exchange.service;

import com.weibi.exchange.model.TradeOrderFinished;

/**
 * 已完成委托 服务类
 *
 * @author jsong
 * @since 2022-03-18
 */
public interface TradeOrderFinishedService {
    /**
     * 插入完成订单
     *
     * @param tradeOrderFinished
     * @return
     */
    int insert(TradeOrderFinished tradeOrderFinished);
}
