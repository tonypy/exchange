package com.weibi.exchange.model;

import java.sql.Timestamp;
import com.weibi.core.commons.api.BaseModel;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 撮合交易对交易评率配置表
 * @author jsong
 * @since 2022-08-19
 */
@Getter
@Setter
@ToString
@ApiModel(value="TradePairFrequencyModel对象", description="撮合交易对交易评率配置表")
public class TradePairFrequency extends BaseModel {


    @ApiModelProperty(value = "主键自增")
    private Integer id;

    @ApiModelProperty(value = "交易对")
    private String pair;

    @ApiModelProperty(value = "等级 0-5 0最快5最慢")
    private Integer pairLevel;

    @ApiModelProperty(value = "创建时间")
    private Timestamp createTime;

    @ApiModelProperty(value = "更新时间")
    private Timestamp updateTime;


}
