package com.weibi.exchange.model;

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
 * @since 2022-07-03
 */
@Getter
@Setter
@ToString
@ApiModel(value="UserModel对象", description="")
public class User extends BaseModel {


    @ApiModelProperty(value = "")
    private String id;

    @ApiModelProperty(value = "手机号码")
    private String tel;

    @ApiModelProperty(value = "手机号码countrycode部分")
    private String telCountryCode;

    @ApiModelProperty(value = "邮箱")
    private String email;

    @ApiModelProperty(value = "防钓鱼码")
    private String antiPhishingCode;

    @ApiModelProperty(value = "nickname")
    private String nickname;

    @ApiModelProperty(value = "国家记录ID")
    private Integer countryId;

    @ApiModelProperty(value = "")
    private String password;

    @ApiModelProperty(value = "vip等级,10为最高等级，1为最低等级")
    private Integer vip;

    @ApiModelProperty(value = "")
    private String payPassword;

    @ApiModelProperty(value = "")
    private String idNo;

    @ApiModelProperty(value = "")
    private String lastLoginIp;

    @ApiModelProperty(value = "")
    private Timestamp lastLoginTime;

    @ApiModelProperty(value = "邀请人用户ID")
    private String inviteBy;

    @ApiModelProperty(value = "用户邀请码			")
    private String inviteCode;

    @ApiModelProperty(value = "USER , ADMIN")
    private String userRole;

    @ApiModelProperty(value = "")
    private String googleAuth;

    @ApiModelProperty(value = "")
    private Integer userStatus;

    @ApiModelProperty(value = "")
    private String wechatId;

    @ApiModelProperty(value = "修改时间")
    private Timestamp updateTime;

    @ApiModelProperty(value = "")
    private Timestamp createTime;

    @ApiModelProperty(value = "注册来源：email-邮箱，phone-手机")
    private String registerFrom;

    @ApiModelProperty(value = "真实姓名")
    private String realName;

    @ApiModelProperty(value = "银行卡绑定状态：0-未绑定，1-已绑定")
    private String bankAuthStatus;

    @ApiModelProperty(value = "身份认证图片url，多张用逗号隔开")
    private String idNoImg;

    @ApiModelProperty(value = "是否志愿者：0-否，1-是")
    private Integer userVolunteerStatus;

    @ApiModelProperty(value = "是否计算盐值：0-否，1-是")
    private Boolean slatStatus;

    @ApiModelProperty(value = "是否谷歌加密：0-否，1-是")
    private Boolean googleStatus;

    @ApiModelProperty(value = "母子账户: 	0: 目账户	1: 子账户")
    private String userType;

    @ApiModelProperty(value = "子账户父级Id")
    private String parentId;

    @ApiModelProperty(value = "用户描述")
    private String userDescribe;

    @ApiModelProperty(value = "备注")
    private String remark;

    @ApiModelProperty(value = "0-未认证，1-基础认证，2-高级认证")
    private Integer identityAuthType;

    @ApiModelProperty(value = "手机验证 1：开启 0：关闭 默认开启")
    private Boolean telOpen;

    @ApiModelProperty(value = "邮箱验证1：开启、0：关闭 默认开启")
    private Boolean emailOpen;

    @ApiModelProperty(value = "谷歌验证打开 0：关闭 1：开启 默认开启")
    private Boolean googleOpen;


}
