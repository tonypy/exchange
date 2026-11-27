package com.weibi.exchange.api;

import com.weibi.config.model.trade.TradeOrder;
import com.weibi.core.commons.api.ApiResponse;
import com.weibi.exchange.model.GetExchangeMemStatistics;
import com.weibi.exchange.model.GetPairExchangeStatistics;
import com.weibi.exchange.model.OrdersDepthVo;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * @author jsong
 * @version 1.0
 * @date 2020-06-13 19:06
 */
@ApiOperation("撮合引擎api")
@FeignClient(name = "${feign.exchange-engine}", path = "exchange-engine")
public interface ExchangeEngineService {

    @GetMapping("/selectOne")
    @ApiOperation(value = "id查询", notes = "id查询")
    Object selectOne(@ApiParam(value = "id") @RequestParam String id);

    @GetMapping("/getQuotationOrdersDepthForDb")
    @ApiOperation(value = "返回交易对盘口信息 会自动合并价格")
    ApiResponse<OrdersDepthVo> getQuotationOrdersDepthForDb(
            @ApiParam(value = "pair") @RequestParam(required = false) String pair,
            @ApiParam(value = "价格深度") @RequestParam(required = false) Integer orderSizeAfterMerged,
            @ApiParam(value = "数据精度") @RequestParam int amountPrecision,
            @ApiParam(value = "价格精度") @RequestParam int pricePrecision);

    @GetMapping("/getPairExchangeStatistics")
    @ApiOperation(value = "交易对撮合统计信息")
    ApiResponse<GetPairExchangeStatistics> getPairExchangeStatistics(
            @ApiParam(value = "pair") @RequestParam(required = false) String pair
    );

    @GetMapping("/findOrderById")
    @ApiOperation(value = "查找订单")
    ApiResponse<TradeOrder> findOrderById(
            @ApiParam(value = "pair") @RequestParam String pair,
            @ApiParam(value = "id") @RequestParam String id);

    @GetMapping("/getExchangeMemStatistics")
    @ApiOperation(value = "撮合内存统计信息")
    ApiResponse<GetExchangeMemStatistics> getExchangeMemStatistics();

    @GetMapping("/getQuotationOrdersDepthForMem")
    @ApiOperation(value = "返回交易对盘口信息 会自动合并价格")
    ApiResponse<OrdersDepthVo> getQuotationOrdersDepthForMem(
            @ApiParam(value = "pair") @RequestParam(required = false) String pair,
            @ApiParam(value = "价格深度") @RequestParam(required = false) Integer orderSizeAfterMerged,
            @ApiParam(value = "数据精度") @RequestParam int amountPrecision,
            @ApiParam(value = "价格精度") @RequestParam int pricePrecision);

    @GetMapping("/getPairMemOrderInfo")
    @ApiOperation(value = "获取交易对内存订单信息")
    ApiResponse<GetPairExchangeStatistics> getPairMemOrderInfo(
            @ApiParam(value = "pair") @RequestParam(required = false) String pair
    );
}
