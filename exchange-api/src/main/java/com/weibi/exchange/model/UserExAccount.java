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
 * 
 * @author jsong
 * @since 2022-02-07
 */
@Getter
@Setter
@ToString
@ApiModel(value="UserExAccountModel对象", description="")
public class UserExAccount extends BaseModel {


    @ApiModelProperty(value = "")
    private String id;

    @ApiModelProperty(value = "")
    private String userId;

    @ApiModelProperty(value = "来源于UserWalletTypeEnum")
    private String walletType;

    @ApiModelProperty(value = "")
    private String coinType;

    @ApiModelProperty(value = "")
    private BigDecimal coinAmount;

    @ApiModelProperty(value = "")
    private BigDecimal coinFroze;

    @ApiModelProperty(value = "")
    private Timestamp updateTime;


}
