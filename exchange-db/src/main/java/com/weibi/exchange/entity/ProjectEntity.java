package com.weibi.exchange.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import java.sql.Timestamp;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 项目方管理
 * @author jsong
 * @since 2022-07-03
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tbl_project")
public class ProjectEntity implements Serializable {

    private static final long serialVersionUID=1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("user_id")
    private String userId;

    @TableField("coin_type")
    private String coinType;

    /**
     * 项目方等级
     */
    @TableField("level")
    private Integer level;

    /**
     * 交易区中的交易对
     */
    @TableField("trade_pair")
    private String tradePair;

    /**
     * 状态，true启用false禁用
     */
    @TableField("status")
    private Boolean status;

    @TableField("create_time")
    private Timestamp createTime;

    @TableField("update_time")
    private Timestamp updateTime;

    /**
     * 删除状态，true:1显示,false:0隐藏
     */
    @TableField("del")
    private Boolean del;

    /**
     * 是否可以提现:0-否，1-是
     */
    @TableField("withdrawal")
    private Boolean withdrawal;


}
