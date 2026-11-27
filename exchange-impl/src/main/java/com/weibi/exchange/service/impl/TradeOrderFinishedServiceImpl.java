package com.weibi.exchange.service.impl;

import cn.hutool.core.convert.Convert;
import com.weibi.core.commons.db.extension.service.impl.BaseServiceImpl;
import com.weibi.exchange.entity.TradeOrderFinishedEntity;
import com.weibi.exchange.mapper.TradeOrderFinishedMapper;
import com.weibi.exchange.model.TradeOrderFinished;
import com.weibi.exchange.service.TradeOrderFinishedService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * 已完成委托 服务实现类
 *
 * @author jsong
 * @since 2022-03-18
 */
@Service
@Primary
public class TradeOrderFinishedServiceImpl extends BaseServiceImpl<TradeOrderFinishedEntity> implements TradeOrderFinishedService {

    @Autowired
    private TradeOrderFinishedMapper tradeOrderFinishedMapper;

    @Override
    public int insert(TradeOrderFinished tradeOrderFinished) {
        return tradeOrderFinishedMapper.insert(Convert.convert(TradeOrderFinishedEntity.class, tradeOrderFinished));
    }
}
