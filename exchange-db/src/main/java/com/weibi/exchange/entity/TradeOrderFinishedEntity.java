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
 * 已完成委托
 * @author jsong
 * @since 2022-03-18
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tbl_trade_order_finished")
public class TradeOrderFinishedEntity implements Serializable {

    private static final long serialVersionUID=1L;

    @TableId("id")
    private String id;

    @TableField("user_id")
    private String userId;

    /**
     * 交易对的名称,DCR_XWC表示以XWC为锚定币种
     */
    @TableField("pair")
    private String pair;

    /**
     * 交易方向，BID表示买入，ASK表示卖出
     */
    @TableField("direction")
    private String direction;

    /**
     * 来源于TradeOrderSrcEnum,表示下单来源
     */
    @TableField("src")
    private String src;

    /**
     * 委托类型，来源于TradeOrderTypeEnum
     */
    @TableField("order_type")
    private String orderType;

    /**
     * 已成交数量
     */
    @TableField("deal_amount")
    private BigDecimal dealAmount;

    /**
     * 总数量
     */
    @TableField("total_amount")
    private BigDecimal totalAmount;

    /**
     * 由于这个订单产生的冻结数
     */
    @TableField("frozen_amount_by_order")
    private BigDecimal frozenAmountByOrder;

    /**
     * 这个订单产生交易之后，赚取到的总数
     */
    @TableField("total_anchor_coin")
    private BigDecimal totalAnchorCoin;

    @TableField("price")
    private BigDecimal price;

    /**
     * 手续费费率
     */
    @TableField("fee_rate")
    private BigDecimal feeRate;

    @TableField("status")
    private Integer status;

    @TableField("referred_by")
    private String referredBy;

    @TableField("created_time")
    private Timestamp createdTime;

    @TableField("update_time")
    private Timestamp updateTime;

    /**
     * 是否抵扣BKK 0：否 1：是
     */
    @TableField("enable_bkk_discount")
    private Boolean enableBkkDiscount;

    /**
     * 来源于UserWalletTypeEnum
     */
    @TableField("wallet_type")
    private String walletType;

    @TableField("operator")
    private String operator;

    @TableField("stop_price")
    private BigDecimal stopPrice;


}
