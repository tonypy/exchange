package com.weibi.exchange.service;

import com.weibi.config.model.trade.TradeDeal;
import com.weibi.exchange.model.TradeStatisticsFee;

/**
 * 交易手续费明细记录表 服务类
 *
 * @author jsong
 * @since 2022-08-14
 */
public interface TradeStatisticsFeeService {
    /**
     * 根据成交记录写入手续费记录
     * @param deal
     * @return
     */
    boolean createFeeRecordByDeal(TradeDeal deal);
}
