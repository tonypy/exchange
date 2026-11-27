package com.weibi.exchange.entity;

import java.math.BigDecimal;
import com.baomidou.mybatisplus.annotation.TableName;
import java.sql.Timestamp;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import java.io.Serializable;
import java.util.Date;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 交易余额记录 只用于生成表结构 真实记录在ss库
 * @author jsong
 * @since 2022-02-10
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tbl_user_balance_change_history")
public class UserBalanceChangeHistoryEntity implements Serializable {

    private static final long serialVersionUID=1L;

    @TableId("id")
    private String id;

    @TableField("business_type")
    private String businessType;

    @TableField("business_key")
    private String businessKey;

    @TableField("user_role")
    private String userRole;

    @TableField("coin_type")
    private String coinType;

    @TableField("user_id")
    private String userId;

    @TableField("wallet_type")
    private String walletType;

    @TableField("available_amount_change")
    private BigDecimal availableAmountChange;

    @TableField("frozen_amount_change")
    private BigDecimal frozenAmountChange;

    @TableField("available_after_change")
    private BigDecimal availableAfterChange;

    @TableField("trans_sequence")
    private Integer transSequence;

    @TableField("frozen_after_change")
    private BigDecimal frozenAfterChange;

    @TableField("business_time")
    private Date businessTime;

    @TableField("result_code")
    private String resultCode;

    /**
     * 备注
     */
    @TableField("remark")
    private String remark;


}
