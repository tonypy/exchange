package com.weibi.exchange.service;

import com.weibi.exchange.model.TradeDealSimplify;

/**
 * 交易记录精简表 仅用于生成表结构 服务类
 *
 * @author jsong
 * @since 2022-02-11
 */
public interface TradeDealSimplifyService {
    /**
     * 插入对手单成交记录
     *
     * @param tradeDealSimplify
     * @return
     */
    int insert(TradeDealSimplify tradeDealSimplify);
}
