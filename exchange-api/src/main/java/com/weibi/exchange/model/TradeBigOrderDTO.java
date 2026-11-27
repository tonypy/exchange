package com.weibi.exchange.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@ApiModel(value="TradeBigOrderDTO对象", description="大单成交自动吃单聚合配置")
public class TradeBigOrderDTO {

    @ApiModelProperty(value = "币对")
    private String pair;

    @ApiModelProperty(value = "触发吃买单比例")
    private BigDecimal bidTriggerTradeRate;

    @ApiModelProperty(value = "触发吃卖单比例")
    private BigDecimal askTriggerTradeRate;

    @ApiModelProperty(value = "吃买单价格档位比例差值")
    private BigDecimal bidTradeGearDifferentRate;

    @ApiModelProperty(value = "吃卖单价格档位比例差值")
    private BigDecimal askTradeGearDifferentRate;

    @ApiModelProperty(value = "吃买单价格档位额度倍数")
    private BigDecimal bidTradeGearQuotaMultiple;

    @ApiModelProperty(value = "吃卖单价格档位额度倍数")
    private BigDecimal askTradeGearQuotaMultiple;

    @ApiModelProperty(value = "触发买吃单预估价格")
    private BigDecimal bidTriggerTradePredictPrice;

    @ApiModelProperty(value = "触发卖吃单预估价格")
    private BigDecimal askTriggerTradePredictPrice;

    @ApiModelProperty(value = "买吃单预估价格所需数量")
    private BigDecimal bidTradeNeedAmount;

    @ApiModelProperty(value = "卖吃单预估价格所需数量")
    private BigDecimal askTradeNeedAmount;

    @ApiModelProperty(value = "自动吃单用户id")
    private String userId;

    @ApiModelProperty(value = "档位配置")
    private List<TradeBigOrderGear> tradeBigOrderGearList;

    @ApiModelProperty(value = "时间戳")
    private Long time;

    @ApiModelProperty(value = "买挂单总数量限制（依照深度计算出的量）")
    private BigDecimal bidTotalLimitAmount;

    @ApiModelProperty(value = "卖挂单总数量限制（依照深度计算出的量）")
    private BigDecimal askTotalLimitAmount;

}
