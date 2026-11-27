package com.weibi.exchange.model;

import java.sql.Timestamp;
import com.weibi.core.commons.api.BaseModel;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 用户个性化设置
 * @author jsong
 * @since 2022-07-03
 */
@Getter
@Setter
@ToString
@ApiModel(value="UserProfileModel对象", description="用户个性化设置")
public class UserProfile extends BaseModel {


    @ApiModelProperty(value = "")
    private Integer id;

    @ApiModelProperty(value = "")
    private String userId;

    @ApiModelProperty(value = "")
    private String profileType;

    @ApiModelProperty(value = "")
    private String profileValue;

    @ApiModelProperty(value = "")
    private Timestamp createTime;

    @ApiModelProperty(value = "")
    private Timestamp updateTime;


}
