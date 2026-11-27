package com.weibi.exchange.service.impl;

import cn.hutool.core.convert.Convert;
import com.weibi.core.commons.db.extension.service.impl.BaseServiceImpl;
import com.weibi.core.commons.logging.ILogging;
import com.weibi.exchange.entity.TradeDealEntity;
import com.weibi.exchange.mapper.TradeDealMapper;
import com.weibi.exchange.model.TradeDeal;
import com.weibi.exchange.service.TradeDealService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * 成交记录表 服务实现类
 *
 * @author jsong
 * @since 2022-02-11
 */
@Service
@Primary
public class TradeDealServiceImpl extends BaseServiceImpl<TradeDealEntity> implements TradeDealService, ILogging {

    @Autowired
    private TradeDealMapper tradeDealMapper;

    @Override
    public int insert(TradeDeal tradeDeal) {
        return tradeDealMapper.insert(Convert.convert(TradeDealEntity.class, tradeDeal));
    }
}
