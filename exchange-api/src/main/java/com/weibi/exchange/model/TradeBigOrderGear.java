package com.weibi.exchange.model;

import com.weibi.core.commons.api.BaseModel;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * 大单成交自动吃单档位配置
 * @author tangxing
 * @since 2022-02-10
 */
@Getter
@Setter
@ToString
@ApiModel(value="TradeBigOrderGearModel对象", description="大单成交自动吃单档位配置")
public class TradeBigOrderGear extends BaseModel {


    @ApiModelProperty(value = "")
    private Long id;

    @ApiModelProperty(value = "tbl_trade_big_order_config配置id")
    private Long configId;

    @ApiModelProperty(value = "吃单价格档位")
    private Integer priceGear;

    @ApiModelProperty(value = "档位价格百分比")
    private BigDecimal gearPriceRate;

    @ApiModelProperty(value = "吃单USDT额度上限")
    private BigDecimal quotaMax;

    @ApiModelProperty(value = "类型：1-bid;2-ask")
    private Integer types;

    @ApiModelProperty(value = "是否删除0否，1是")
    private Boolean delFlag;

    @ApiModelProperty(value = "")
    private Timestamp createTime;

    @ApiModelProperty(value = "")
    private Timestamp updateTime;


}
