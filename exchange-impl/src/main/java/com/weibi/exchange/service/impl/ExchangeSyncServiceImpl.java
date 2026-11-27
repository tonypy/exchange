package com.weibi.exchange.service.impl;

import com.alibaba.fastjson.JSON;
import com.weibi.core.commons.db.extension.service.impl.BaseServiceImpl;
import com.weibi.core.commons.logging.ILogging;
import com.weibi.exchange.api.ExchangeSyncService;
import com.weibi.exchange.entity.TestEntity;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * @author jsong
 * @version 1.0
 * @date 2020-06-13 19:08
 */
@Service
@Primary
public class ExchangeSyncServiceImpl extends BaseServiceImpl<TestEntity> implements ExchangeSyncService, ILogging {

    @Override
    public Object selectOne(String id) {
        // 查询一条数据 demo
        var result = lambdaQuery().select()
                .eq(TestEntity::getId, id)
                .one();
        warn(JSON.toJSONString(result));
        return "123";
    }
}
