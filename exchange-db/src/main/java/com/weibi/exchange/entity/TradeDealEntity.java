package com.weibi.exchange.entity;

import java.math.BigDecimal;
import com.baomidou.mybatisplus.annotation.TableName;
import java.sql.Timestamp;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 成交记录表
 * @author jsong
 * @since 2022-02-11
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tbl_trade_deal")
public class TradeDealEntity implements Serializable {

    private static final long serialVersionUID=1L;

    @TableId("id")
    private String id;

    @TableField("buyer_id")
    private String buyerId;

    @TableField("seller_id")
    private String sellerId;

    /**
     * 交易对的名称,WNT-XWC表示以XWC为锚定币种
     */
    @TableField("pair")
    private String pair;

    /**
     * 已成交数量
     */
    @TableField("deal_amount")
    private BigDecimal dealAmount;

    @TableField("price")
    private BigDecimal price;

    /**
     * 主动成交方向，如果是主动发起买单成交，代表外盘，值是B， 如果是主动卖单来成交，代表内盘，值是S
     */
    @TableField("deal_direction")
    private String dealDirection;

    /**
     * 买家手续费，币种是代币币种
     */
    @TableField("buyer_fee")
    private BigDecimal buyerFee;

    /**
     * 卖家手续费，币种是锚定币币种
     */
    @TableField("seller_fee")
    private BigDecimal sellerFee;

    @TableField("buyer_fee_coin_type")
    private String buyerFeeCoinType;
    @TableField("seller_fee_coin_type")
    private String sellerFeeCoinType;
    @TableField("buyer_src")
    private String buyerSrc;
    @TableField("seller_src")
    private String sellerSrc;

    /**
     * 关联的买家订单号
     */
    @TableField("ref_buy_order")
    private String refBuyOrder;

    /**
     * 关联的卖家订单号
     */
    @TableField("ref_sell_order")
    private String refSellOrder;

    @TableField("buyer_referred_by")
    private String buyerReferredBy;

    @TableField("seller_referred_by")
    private String sellerReferredBy;

    @TableField("created_time")
    private Timestamp createdTime;


}
