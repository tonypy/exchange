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
 * 已完成委托
 * @author jsong
 * @since 2022-03-18
 */
@Getter
@Setter
@ToString
@ApiModel(value="TradeOrderFinishedModel对象", description="已完成委托")
public class TradeOrderFinished extends BaseModel {


    @ApiModelProperty(value = "")
    private String id;

    @ApiModelProperty(value = "")
    private String userId;

    @ApiModelProperty(value = "交易对的名称,DCR_XWC表示以XWC为锚定币种")
    private String pair;

    @ApiModelProperty(value = "交易方向，BID表示买入，ASK表示卖出")
    private String direction;

    @ApiModelProperty(value = "来源于TradeOrderSrcEnum,表示下单来源")
    private String src;

    @ApiModelProperty(value = "委托类型，来源于TradeOrderTypeEnum")
    private String orderType;

    @ApiModelProperty(value = "已成交数量")
    private BigDecimal dealAmount;

    @ApiModelProperty(value = "总数量")
    private BigDecimal totalAmount;

    @ApiModelProperty(value = "由于这个订单产生的冻结数")
    private BigDecimal frozenAmountByOrder;

    @ApiModelProperty(value = "这个订单产生交易之后，赚取到的总数")
    private BigDecimal totalAnchorCoin;

    @ApiModelProperty(value = "")
    private BigDecimal price;

    @ApiModelProperty(value = "手续费费率")
    private BigDecimal feeRate;

    @ApiModelProperty(value = "")
    private Integer status;

    @ApiModelProperty(value = "")
    private String referredBy;

    @ApiModelProperty(value = "")
    private Timestamp createdTime;

    @ApiModelProperty(value = "")
    private Timestamp updateTime;

    @ApiModelProperty(value = "是否抵扣BKK 0：否 1：是")
    private Boolean enableBkkDiscount;

    @ApiModelProperty(value = "来源于UserWalletTypeEnum")
    private String walletType;

    @ApiModelProperty(value = "")
    private String operator;

    @ApiModelProperty(value = "")
    private BigDecimal stopPrice;


}
