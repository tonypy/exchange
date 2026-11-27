package com.weibi.exchange.model;

import java.math.BigDecimal;
import java.sql.Timestamp;
import com.weibi.core.commons.api.BaseModel;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 交易手续费明细记录表
 * @author jsong
 * @since 2022-08-14
 */
@Getter
@Setter
@ToString
@ApiModel(value="TradeStatisticsFeeModel对象", description="交易手续费明细记录表")
public class TradeStatisticsFee extends BaseModel {


    @ApiModelProperty(value = "")
    private String id;

    @ApiModelProperty(value = "对应的成交记录id")
    private String dealId;

    @ApiModelProperty(value = "用户ID")
    private String userId;

    @ApiModelProperty(value = "交易达成时间，用来区分用户交易日期")
    private Timestamp dealTime;

    @ApiModelProperty(value = "交易对,BTC_USDT格式")
    private String pair;

    @ApiModelProperty(value = "手续费币种类型")
    private String feeCoinType;

    @ApiModelProperty(value = "代币币种手续费对应的金额，单位是代币")
    private BigDecimal feeCoinAmount;

    @ApiModelProperty(value = "计算手续费的时刻，代币币种手续费对应的USDT价格")
    private BigDecimal feeUsdtPrice;

    @ApiModelProperty(value = "代币手续费对应的usdt手续费金额，单位是USDT")
    private BigDecimal feeUsdtAmount;

    @ApiModelProperty(value = "")
    private String orderSrc;

    @ApiModelProperty(value = "成交时候对应的用户委托，用户所下单的方向，来自TradeDirectionEnum枚举类")
    private String orderDirection;

    @ApiModelProperty(value = "手续费转账状态，0：未转账，1：已转账")
    private Integer transferStatus;

    @ApiModelProperty(value = "主动成交方向，如果是主动发起买单成交，代表外盘，值是B， 如果是主动卖单来成交，代表内盘，值是S，来自TradeDealDirectionEnum")
    private String dealDirection;

    @ApiModelProperty(value = "")
    private Timestamp updateTime;

    @ApiModelProperty(value = "")
    private Timestamp createdTime;

    @ApiModelProperty(value = "")
    private Timestamp miningDate;


}
