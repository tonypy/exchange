package com.weibi.exchange.service;

import com.weibi.exchange.model.TradeDeal;

/**
 * 成交记录表 服务类
 *
 * @author jsong
 * @since 2022-02-11
 */
public interface TradeDealService {
    /**
     * 插入交易记录
     *
     * @param tradeDeal
     * @return
     */
    int insert(TradeDeal tradeDeal);
}
