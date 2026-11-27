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
 * 交易余额记录 只用于生成表结构 真实记录在ss库
 * @author jsong
 * @since 2022-02-10
 */
@Getter
@Setter
@ToString
@ApiModel(value="UserBalanceChangeHistoryModel对象", description="交易余额记录 只用于生成表结构 真实记录在ss库")
public class UserBalanceChangeHistory extends BaseModel {


    @ApiModelProperty(value = "")
    private String id;

    @ApiModelProperty(value = "")
    private String businessType;

    @ApiModelProperty(value = "")
    private String businessKey;

    @ApiModelProperty(value = "")
    private String userRole;

    @ApiModelProperty(value = "")
    private String coinType;

    @ApiModelProperty(value = "")
    private String userId;

    @ApiModelProperty(value = "")
    private String walletType;

    @ApiModelProperty(value = "")
    private BigDecimal availableAmountChange;

    @ApiModelProperty(value = "")
    private BigDecimal frozenAmountChange;

    @ApiModelProperty(value = "")
    private BigDecimal availableAfterChange;

    @ApiModelProperty(value = "")
    private Integer transSequence;

    @ApiModelProperty(value = "")
    private BigDecimal frozenAfterChange;

    @ApiModelProperty(value = "")
    private Timestamp businessTime;

    @ApiModelProperty(value = "")
    private String resultCode;

    @ApiModelProperty(value = "备注")
    private String remark;


}
