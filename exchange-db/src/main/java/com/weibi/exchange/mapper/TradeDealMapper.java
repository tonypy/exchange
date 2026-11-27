package com.weibi.exchange.mapper;

import com.weibi.exchange.entity.TradeDealEntity;
import com.weibi.core.commons.db.extension.mapper.BaseDao;
import org.apache.ibatis.annotations.Insert;

/**
 * 成交记录表 Mapper 接口
 * @author jsong
 * @since 2022-02-11
 */
public interface TradeDealMapper extends BaseDao<TradeDealEntity> {
    @Insert({
            "insert into tbl_trade_deal (id, buyer_id, ",
            "seller_id, pair, ",
            "deal_amount, price, ",
            "deal_direction, buyer_src, ",
            "seller_src, buyer_fee_coin_type, ",
            "seller_fee_coin_type, buyer_fee, ",
            "seller_fee, ref_buy_order, ",
            "ref_sell_order, buyer_referred_by, ",
            "seller_referred_by, created_time)",
            "values (#{id,jdbcType=VARCHAR}, #{buyerId,jdbcType=VARCHAR}, ",
            "#{sellerId,jdbcType=VARCHAR}, #{pair,jdbcType=VARCHAR}, ",
            "#{dealAmount,jdbcType=DECIMAL}, #{price,jdbcType=DECIMAL}, ",
            "#{dealDirection,jdbcType=VARCHAR}, #{buyerSrc,jdbcType=VARCHAR}, ",
            "#{sellerSrc,jdbcType=VARCHAR}, #{buyerFeeCoinType,jdbcType=VARCHAR}, ",
            "#{sellerFeeCoinType,jdbcType=VARCHAR}, #{buyerFee,jdbcType=DECIMAL}, ",
            "#{sellerFee,jdbcType=DECIMAL}, #{refBuyOrder,jdbcType=VARCHAR}, ",
            "#{refSellOrder,jdbcType=VARCHAR}, #{buyerReferredBy,jdbcType=VARCHAR}, ",
            "#{sellerReferredBy,jdbcType=VARCHAR}, #{createdTime,jdbcType=TIMESTAMP})"
    })
    int insertTradeDeal(TradeDealEntity record);
}
