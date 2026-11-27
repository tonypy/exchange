package com.weibi.exchange.model;

import java.math.BigDecimal;
import java.sql.Timestamp;

import com.baomidou.mybatisplus.annotation.TableField;
import com.weibi.core.commons.api.BaseModel;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 成交记录表
 * @author jsong
 * @since 2022-02-11
 */
@Getter
@Setter
@ToString
@ApiModel(value="TradeDealModel对象", description="成交记录表")
public class TradeDeal extends BaseModel {


    @ApiModelProperty(value = "")
    private String id;

    @ApiModelProperty(value = "")
    private String buyerId;

    @ApiModelProperty(value = "")
    private String sellerId;

    @ApiModelProperty(value = "交易对的名称,WNT-XWC表示以XWC为锚定币种")
    private String pair;

    @ApiModelProperty(value = "已成交数量")
    private BigDecimal dealAmount;

    @ApiModelProperty(value = "")
    private BigDecimal price;

    @ApiModelProperty(value = "主动成交方向，如果是主动发起买单成交，代表外盘，值是B， 如果是主动卖单来成交，代表内盘，值是S")
    private String dealDirection;

    @ApiModelProperty(value = "买家手续费，币种是代币币种")
    private BigDecimal buyerFee;

    @ApiModelProperty(value = "卖家手续费，币种是锚定币币种")
    private BigDecimal sellerFee;

    @ApiModelProperty(value = "买方手续费币种")
    private String buyerFeeCoinType;
    @ApiModelProperty(value = "卖方手续费币种")
    private String sellerFeeCoinType;
    @ApiModelProperty(value = "买方来源")
    private String buyerSrc;
    @ApiModelProperty(value = "卖方来源")
    private String sellerSrc;

    @ApiModelProperty(value = "关联的买家订单号")
    private String refBuyOrder;

    @ApiModelProperty(value = "关联的卖家订单号")
    private String refSellOrder;

    @ApiModelProperty(value = "")
    private String buyerReferredBy;

    @ApiModelProperty(value = "")
    private String sellerReferredBy;

    @ApiModelProperty(value = "")
    private Timestamp createdTime;


}
