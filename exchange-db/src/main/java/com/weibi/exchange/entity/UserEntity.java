package com.weibi.exchange.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.sql.Timestamp;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 
 * @author jsong
 * @since 2022-07-03
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tbl_user")
public class UserEntity implements Serializable {

    private static final long serialVersionUID=1L;

    @TableId("id")
    private String id;

    /**
     * 手机号码
     */
    @TableField("tel")
    private String tel;

    /**
     * 手机号码countrycode部分
     */
    @TableField("tel_country_code")
    private String telCountryCode;

    /**
     * 邮箱
     */
    @TableField("email")
    private String email;

    /**
     * 防钓鱼码
     */
    @TableField("anti_phishing_code")
    private String antiPhishingCode;

    /**
     * nickname
     */
    @TableField("nickname")
    private String nickname;

    /**
     * 国家记录ID
     */
    @TableField("country_id")
    private Integer countryId;

    @TableField("password")
    private String password;

    /**
     * vip等级,10为最高等级，1为最低等级
     */
    @TableField("vip")
    private Integer vip;

    @TableField("pay_password")
    private String payPassword;

    @TableField("id_no")
    private String idNo;

    @TableField("last_login_ip")
    private String lastLoginIp;

    @TableField("last_login_time")
    private Timestamp lastLoginTime;

    /**
     * 邀请人用户ID
     */
    @TableField("invite_by")
    private String inviteBy;

    /**
     * 用户邀请码			
     */
    @TableField("invite_code")
    private String inviteCode;

    /**
     * USER , ADMIN
     */
    @TableField("user_role")
    private String userRole;

    @TableField("google_auth")
    private String googleAuth;

    @TableField("user_status")
    private Integer userStatus;

    @TableField("wechat_id")
    private String wechatId;

    /**
     * 修改时间
     */
    @TableField("update_time")
    private Timestamp updateTime;

    @TableField("create_time")
    private Timestamp createTime;

    /**
     * 注册来源：email-邮箱，phone-手机
     */
    @TableField("register_from")
    private String registerFrom;

    /**
     * 真实姓名
     */
    @TableField("real_name")
    private String realName;

    /**
     * 银行卡绑定状态：0-未绑定，1-已绑定
     */
    @TableField("bank_auth_status")
    private String bankAuthStatus;

    /**
     * 身份认证图片url，多张用逗号隔开
     */
    @TableField("id_no_img")
    private String idNoImg;

    /**
     * 是否志愿者：0-否，1-是
     */
    @TableField("user_volunteer_status")
    private Integer userVolunteerStatus;

    /**
     * 是否计算盐值：0-否，1-是
     */
    @TableField("slat_status")
    private Boolean slatStatus;

    /**
     * 是否谷歌加密：0-否，1-是
     */
    @TableField("google_status")
    private Boolean googleStatus;

    /**
     * 母子账户: 	0: 目账户	1: 子账户
     */
    @TableField("user_type")
    private String userType;

    /**
     * 子账户父级Id
     */
    @TableField("parent_id")
    private String parentId;

    /**
     * 用户描述
     */
    @TableField("user_describe")
    private String userDescribe;

    /**
     * 备注
     */
    @TableField("remark")
    private String remark;

    /**
     * 0-未认证，1-基础认证，2-高级认证
     */
    @TableField("identity_auth_type")
    private Integer identityAuthType;

    /**
     * 手机验证 1：开启 0：关闭 默认开启
     */
    @TableField("tel_open")
    private Boolean telOpen;

    /**
     * 邮箱验证1：开启、0：关闭 默认开启
     */
    @TableField("email_open")
    private Boolean emailOpen;

    /**
     * 谷歌验证打开 0：关闭 1：开启 默认开启
     */
    @TableField("google_open")
    private Boolean googleOpen;


}
