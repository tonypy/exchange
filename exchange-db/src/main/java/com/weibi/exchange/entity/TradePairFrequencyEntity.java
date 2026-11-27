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
 * 撮合交易对交易评率配置表
 * @author jsong
 * @since 2022-08-19
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tbl_trade_pair_frequency")
public class TradePairFrequencyEntity implements Serializable {

    private static final long serialVersionUID=1L;

    /**
     * 主键自增
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 交易对
     */
    @TableField("pair")
    private String pair;

    /**
     * 等级 0-5 0最快5最慢
     */
    @TableField("pair_level")
    private Integer pairLevel;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private Timestamp createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private Timestamp updateTime;


}
