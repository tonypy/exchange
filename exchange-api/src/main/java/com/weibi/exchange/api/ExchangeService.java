package com.weibi.exchange.api;

import com.weibi.config.model.trade.TradeOrder;
import com.weibi.core.commons.api.ApiResponse;
import com.weibi.core.commons.model.mybatis.BasePage;
import com.weibi.exchange.model.BatchCancelOrderResult;
import com.weibi.exchange.model.BatchCancelParamDTO;
import com.weibi.exchange.model.OrdersDepthVo;
import com.weibi.exchange.model.TradeOrderUnfinished;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;

/**
 * @author jsong
 * @version 1.0
 * @date 2020-06-13 19:06
 */
@ApiOperation("交易相关API")
@FeignClient(name = "${feign.exchange-rest}", path = "exchange")
public interface ExchangeService {

    @PostMapping("/createOrderForUser")
    @ApiOperation(value = "用户下单")
    ApiResponse createOrderForUser(
            @ApiParam("必选，交易用户") @RequestParam String userId,
            @ApiParam("必选，交易对") @RequestParam String pair,
            @ApiParam("必选，交易方向(BID:买入,ASK:卖出)") @RequestParam String direction,
            @ApiParam("必选,交易总量") @RequestParam BigDecimal amount,
            @ApiParam("钱包类型,WALLET-现货(默认)") @RequestParam(required = false, defaultValue = "WALLET") String walletType,
            @ApiParam("委托类型,LIMIT-限价(默认）， MARKET-市价, STOP_LIMIT-止盈止损") @RequestParam(defaultValue = "LIMIT") String orderType,
            @ApiParam("可选，对于市价委托，不需要传递，其余类型必须传递") @RequestParam(required = false) BigDecimal price,
            @ApiParam("触发价格，用于止盈止损(计划)委托") @RequestParam(required = false) BigDecimal stopPrice,
            @ApiParam("lte-小于等于， gte-大于等于，在止盈止损委托才会用上") @RequestParam(required = false) String operator,
            @ApiParam("下单来源 pc或者app TradeOrderSrcEnum") @RequestParam(required = false, defaultValue = "UNKNOWN") String src);

    @PostMapping("/createOrderForMachine")
    @ApiOperation(value = "机器人下单")
    ApiResponse createOrderForMachine(
            @ApiParam("必选，交易用户") @RequestParam String userId,
            @ApiParam("必选，交易对") @RequestParam String pair,
            @ApiParam("必选，交易方向(BID:买入,ASK:卖出)") @RequestParam String direction,
            @ApiParam("必选,交易总量") @RequestParam BigDecimal amount,
            @ApiParam("钱包类型,WALLET-现货(默认)") @RequestParam(required = false, defaultValue = "WALLET") String walletType,
            @ApiParam("委托类型,LIMIT-限价(默认）， MARKET-市价, STOP_LIMIT-止盈止损") @RequestParam(required = false, defaultValue = "LIMIT") String orderType,
            @ApiParam("可选，对于市价委托，不需要传递，其余类型必须传递") @RequestParam(required = false) BigDecimal price);

    @PostMapping("/createOrderForOpenApi")
    @ApiOperation(value = "openapi下单")
    ApiResponse createOrderForOpenApi(
            @ApiParam("必选，交易用户") @RequestParam String userId,
            @ApiParam("必选，交易对") @RequestParam String pair,
            @ApiParam("必选，交易方向(BID:买入,ASK:卖出)") @RequestParam String direction,
            @ApiParam("必选,交易总量") @RequestParam BigDecimal amount,
            @ApiParam("钱包类型,WALLET-现货(默认)") @RequestParam(required = false, defaultValue = "WALLET") String walletType,
            @ApiParam("委托类型,LIMIT-限价(默认）， MARKET-市价, STOP_LIMIT-止盈止损") @RequestParam(required = false, defaultValue = "LIMIT") String orderType,
            @ApiParam("可选，对于市价委托，不需要传递，其余类型必须传递") @RequestParam(required = false) BigDecimal price,
            @ApiParam("触发价格，用于止盈止损(计划)委托") @RequestParam(required = false) BigDecimal stopPrice,
            @ApiParam("lte-小于等于， gte-大于等于，在止盈止损委托才会用上") @RequestParam(required = false) String operator);

    @PostMapping("/cancelOrderForUser")
    @ApiOperation(value = "用户撤单")
    ApiResponse cancelOrderForUser(
            @ApiParam("必选，交易用户") @RequestParam String userId,
            @ApiParam("必选，交易订单号") @RequestParam String orderNo,
            @ApiParam("必选，交易对") @RequestParam(required = false) String pair);

    @PostMapping("/cancelOrderForMachine")
    @ApiOperation(value = "机器人撤单")
    ApiResponse cancelOrderForMachine(
            @ApiParam("必选，交易用户") @RequestParam String userId,
            @ApiParam("必选，交易订单号") @RequestParam String orderNo,
            @ApiParam("必选，交易对") @RequestParam(required = false) String pair);

    @PostMapping("/cancelOrderForOpenApi")
    @ApiOperation(value = "openapi撤单")
    ApiResponse cancelOrderForOpenApi(
            @ApiParam("必选，交易用户") @RequestParam String userId,
            @ApiParam("必选，交易订单号") @RequestParam String orderNo,
            @ApiParam("必选，交易对") @RequestParam(required = false) String pair);

    /**
     * 激活委托订单
     *
     * @param tradeOrder
     * @return
     */
    ApiResponse activeStopLimitOrder(TradeOrder tradeOrder);

    @PostMapping("/activeStopLimitDeal")
    @ApiOperation(value = "模拟成交触发计划委托, 测试使用", hidden = true)
    @Deprecated
    ApiResponse activeStopLimitDeal(
            @ApiParam("必选，交易对") @RequestParam String pair,
            @ApiParam("必选,交易总量") @RequestParam BigDecimal amount,
            @ApiParam("必选，交易方向(B:买入,S:卖出)") @RequestParam String direction,
            @ApiParam("必选，成交价") @RequestParam BigDecimal price);

    @GetMapping("/getQuotationOrdersDepth")
    @ApiOperation(value = "返回交易对盘口信息 会自动合并价格")
    ApiResponse<OrdersDepthVo> getQuotationOrdersDepth(
            @ApiParam(value = "pair") @RequestParam(required = false) String pair,
            @ApiParam(value = "价格深度") @RequestParam(required = false) Integer depth,
            @ApiParam(value = "数据精度") @RequestParam int amountPrecision,
            @ApiParam(value = "价格精度") @RequestParam int pricePrecision);

    @ApiOperation(value = "获取未完成订单列表 订单ID、用户ID、交易对参数必须传一个，不能查询全部订单")
    @GetMapping("/getTradeOrderUnfinishedList")
    BasePage<TradeOrderUnfinished> getTradeOrderUnfinishedList(
            @ApiParam("订单ID") @RequestParam(required = false) String id,
            @ApiParam("用户ID") @RequestParam(required = false) String userId,
            @ApiParam("交易对") @RequestParam(required = false) String pair,
            @ApiParam("交易方向 (BID:买入,ASK:卖出)") @RequestParam(required = false) String direction,
            @ApiParam("订单来源 TradeOrderSrcEnum") @RequestParam(required = false) String src,
            @ApiParam("订单类型 LIMIT-限价(默认）， MARKET-市价, STOP_LIMIT-止盈止损") @RequestParam(required = false) String orderType,
            @ApiParam("订单状态") @RequestParam(required = false) Integer status,
            @ApiParam("页码") @RequestParam(required = false, defaultValue = "1") Integer page,
            @ApiParam("页大小") @RequestParam(required = false, defaultValue = "10") Integer pageSize);

    @ApiOperation(value = "获取用户未完成订单的交易币对列表")
    @GetMapping("/getPairForTradeOrderUnfinished")
    ApiResponse<List<String>> getPairForTradeOrderUnfinished(@ApiParam("用户ID") @RequestParam String userId);


    @PostMapping("/batchCancelOrder")
    @ApiOperation(value = "管理端批量撤单，三个参数必须传一个")
    ApiResponse<List<BatchCancelOrderResult>> batchCancelOrder(
            @ApiParam("可选 订单id集合，如果集合为空则撤销用户所有订单") @RequestBody BatchCancelParamDTO batchCancelParamDTO);
}
