package com.weibi.exchange.service.impl;

import cn.hutool.core.convert.Convert;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.weibi.core.commons.db.extension.service.impl.BaseServiceImpl;
import com.weibi.exchange.entity.TradePairFrequencyEntity;
import com.weibi.exchange.mapper.TradePairFrequencyMapper;
import com.weibi.exchange.model.TradePairFrequency;
import com.weibi.exchange.service.TradePairFrequencyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 撮合交易对交易评率配置表 服务实现类
 *
 * @author jsong
 * @since 2022-08-19
 */
@Service
public class TradePairFrequencyServiceImpl extends BaseServiceImpl<TradePairFrequencyEntity> implements TradePairFrequencyService {
    @Autowired
    private TradePairFrequencyMapper tradePairFrequencyMapper;

    @Override
    public List<TradePairFrequency> getTradePairFrequencyList() {
        return Convert.toList(TradePairFrequency.class, tradePairFrequencyMapper.selectList(new QueryWrapper<>()));
    }
}
