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
 * 交易手续费明细记录表
 * @author jsong
 * @since 2022-08-14
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tbl_trade_statistics_fee")
public class TradeStatisticsFeeEntity implements Serializable {

    private static final long serialVersionUID=1L;

    @TableField("id")
    private String id;

    /**
     * 对应的成交记录id
     */
    @TableId("deal_id")
    private String dealId;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private String userId;

    /**
     * 交易达成时间，用来区分用户交易日期
     */
    @TableField("deal_time")
    private Timestamp dealTime;

    /**
     * 交易对,BTC_USDT格式
     */
    @TableField("pair")
    private String pair;

    /**
     * 手续费币种类型
     */
    @TableField("fee_coin_type")
    private String feeCoinType;

    /**
     * 代币币种手续费对应的金额，单位是代币
     */
    @TableField("fee_coin_amount")
    private BigDecimal feeCoinAmount;

    /**
     * 计算手续费的时刻，代币币种手续费对应的USDT价格
     */
    @TableField("fee_usdt_price")
    private BigDecimal feeUsdtPrice;

    /**
     * 代币手续费对应的usdt手续费金额，单位是USDT
     */
    @TableField("fee_usdt_amount")
    private BigDecimal feeUsdtAmount;

    @TableField("order_src")
    private String orderSrc;

    /**
     * 成交时候对应的用户委托，用户所下单的方向，来自TradeDirectionEnum枚举类
     */
    @TableField("order_direction")
    private String orderDirection;

    /**
     * 手续费转账状态，0：未转账，1：已转账
     */
    @TableField("transfer_status")
    private Integer transferStatus;

    /**
     * 主动成交方向，如果是主动发起买单成交，代表外盘，值是B， 如果是主动卖单来成交，代表内盘，值是S，来自TradeDealDirectionEnum
     */
    @TableField("deal_direction")
    private String dealDirection;

    @TableField("update_time")
    private Timestamp updateTime;

    @TableField("created_time")
    private Timestamp createdTime;

    @TableField("mining_date")
    private Timestamp miningDate;


}
