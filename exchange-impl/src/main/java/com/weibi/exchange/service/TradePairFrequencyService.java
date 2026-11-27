package com.weibi.exchange.service;

import com.weibi.exchange.model.TradePairFrequency;

import java.util.List;

/**
 * 撮合交易对交易评率配置表 服务类
 *
 * @author jsong
 * @since 2022-08-19
 */
public interface TradePairFrequencyService {
    /**
     * 获取币种等级配置
     *
     * @return
     */
    List<TradePairFrequency> getTradePairFrequencyList();
}
