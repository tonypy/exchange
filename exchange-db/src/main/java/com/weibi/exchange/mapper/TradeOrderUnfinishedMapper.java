package com.weibi.exchange.mapper;

import com.weibi.core.commons.db.extension.mapper.BaseDao;
import com.weibi.exchange.entity.TradeOrderUnfinishedEntity;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 挂单表 Mapper 接口
 *
 * @author jsong
 * @since 2022-02-07
 */
public interface TradeOrderUnfinishedMapper extends BaseDao<TradeOrderUnfinishedEntity> {


    @Select("select pair from bkex_exchange.tbl_trade_order_unfinished where user_id =#{userId,jdbcType=VARCHAR} group by pair")
    List<String> getPairByUserId(String userId);
}
