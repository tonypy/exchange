package com.weibi.exchange.entity;

import java.math.BigDecimal;
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
 * @since 2022-08-18
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("_tmp_tbl_user_ex_account")
public class TmpTblUserExAccountEntity implements Serializable {

    private static final long serialVersionUID=1L;

    @TableId("id")
    private String id;

    @TableField("user_id")
    private String userId;

    /**
     * 来源于UserWalletTypeEnum
     */
    @TableField("wallet_type")
    private String walletType;

    @TableField("coin_type")
    private String coinType;

    @TableField("coin_amount")
    private BigDecimal coinAmount;

    @TableField("coin_froze")
    private BigDecimal coinFroze;

    @TableField("update_time")
    private Timestamp updateTime;


}
