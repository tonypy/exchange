package com.weibi.exchange.service.impl;

import cn.hutool.core.convert.Convert;
import com.weibi.core.commons.db.extension.service.impl.BaseServiceImpl;
import com.weibi.exchange.entity.TradeDealSimplifyEntity;
import com.weibi.exchange.mapper.TradeDealSimplifyMapper;
import com.weibi.exchange.model.TradeDealSimplify;
import com.weibi.exchange.service.TradeDealSimplifyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * 交易记录精简表 仅用于生成表结构 服务实现类
 *
 * @author jsong
 * @since 2022-02-11
 */
@Service
@Primary
public class TradeDealSimplifyServiceImpl extends BaseServiceImpl<TradeDealSimplifyEntity> implements TradeDealSimplifyService {

    @Autowired
    private TradeDealSimplifyMapper tradeDealSimplifyMapper;

    @Override
    public int insert(TradeDealSimplify tradeDealSimplify) {
        return tradeDealSimplifyMapper.insert(Convert.convert(TradeDealSimplifyEntity.class, tradeDealSimplify));
    }
}
