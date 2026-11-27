package com.weibi.exchange.model;

import java.sql.Timestamp;
import com.weibi.core.commons.api.BaseModel;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 项目方管理
 * @author jsong
 * @since 2022-07-03
 */
@Getter
@Setter
@ToString
@ApiModel(value="ProjectModel对象", description="项目方管理")
public class Project extends BaseModel {


    @ApiModelProperty(value = "")
    private Integer id;

    @ApiModelProperty(value = "")
    private String userId;

    @ApiModelProperty(value = "")
    private String coinType;

    @ApiModelProperty(value = "项目方等级")
    private Integer level;

    @ApiModelProperty(value = "交易区中的交易对")
    private String tradePair;

    @ApiModelProperty(value = "状态，true启用false禁用")
    private Boolean status;

    @ApiModelProperty(value = "")
    private Timestamp createTime;

    @ApiModelProperty(value = "")
    private Timestamp updateTime;

    @ApiModelProperty(value = "删除状态，true:1显示,false:0隐藏")
    private Boolean del;

    @ApiModelProperty(value = "是否可以提现:0-否，1-是")
    private Boolean withdrawal;


}
