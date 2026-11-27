package com.weibi.exchange.model;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiParam;
import lombok.Data;

import java.util.List;

/**
 * 批量撤单DTO
 *
 * @author killua
 * @date 2022/10/19 21:49
 */
@Data
@ApiModel(value = "批量撤单DTO", description = "批量撤单参数对象")
public class BatchCancelParamDTO {

    @ApiModelProperty("可选 订单id集合，如果集合为空则撤销用户所有订单")
    List<String> ids;

    @ApiParam("可选，用户ID")
    String userId;

    @ApiParam("可选，交易对")
    String pair;
}
