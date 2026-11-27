package com.weibi.exchange.service.impl;

import com.alibaba.fastjson.JSON;
import com.weibi.config.model.trade.TradeOrder;
import com.weibi.core.commons.api.ApiResponse;
import com.weibi.core.commons.db.extension.service.impl.BaseServiceImpl;
import com.weibi.core.commons.logging.ILogging;
import com.weibi.exchange.api.ExchangeEngineService;
import com.weibi.exchange.core.CoinTrader;
import com.weibi.exchange.core.CoinTraderFactory;
import com.weibi.exchange.core.MergeOrder;
import com.weibi.exchange.entity.TestEntity;
import com.weibi.exchange.model.*;
import com.weibi.exchange.service.TradeOrderUnfinishedService;
import com.weibi.exchange.util.ExchangeKafkaUtil;
import com.weibi.exchange.util.LocalCacheUtil;
import lombok.var;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @author huopengbo
 * @version 1.0
 * @date 2020-06-13 19:08
 */
@Service
@Primary
public class ExchangeEngineServiceImpl extends BaseServiceImpl<TestEntity> implements ExchangeEngineService, ILogging {

    @Autowired
    private TradeOrderUnfinishedService tradeOrderUnfinishedService;
    @Autowired
    private CoinTraderFactory coinTraderFactory;
    @Autowired
    private LocalCacheUtil localCacheUtil;
    @Autowired
    private ExchangeKafkaUtil exchangeKafkaUtil;

    @Override
    public Object selectOne(String id) {
        // 查询一条数据 demo
        var result = lambdaQuery().select()
                .eq(TestEntity::getId, id)
                .one();
        warn(JSON.toJSONString(result));
        return "123";
    }

    @Override
    public ApiResponse<OrdersDepthVo> getQuotationOrdersDepthForDb(String pair, Integer orderSizeAfterMerged, int amountPrecision, int pricePrecision) {
        List<OrdersDepthDTO> bidDepth = tradeOrderUnfinishedService.getOrdersDepth(pair, true, orderSizeAfterMerged == null ? 20 : orderSizeAfterMerged);
        List<OrdersDepthDTO> askDepth = tradeOrderUnfinishedService.getOrdersDepth(pair, false, orderSizeAfterMerged == null ? 20 : orderSizeAfterMerged);

        OrdersDepthVo ordersDepthVo = new OrdersDepthVo();
        if (bidDepth != null && bidDepth.size() > 0) {
            ordersDepthVo.setBidDepth(bidDepth.stream().map(item -> {
                OrdersDepthItemVo ordersDepthItemVo = new OrdersDepthItemVo();
                ordersDepthItemVo.setPrice(item.getPrice());
                ordersDepthItemVo.setExchangeAmount(item.getExchangeAmount());
                return ordersDepthItemVo;
            }).collect(Collectors.toList()));
        }
        if (askDepth != null && askDepth.size() > 0) {
            ordersDepthVo.setAskDepth(askDepth.stream().map(item -> {
                OrdersDepthItemVo ordersDepthItemVo = new OrdersDepthItemVo();
                ordersDepthItemVo.setPrice(item.getPrice());
                ordersDepthItemVo.setExchangeAmount(item.getExchangeAmount());
                return ordersDepthItemVo;
            }).collect(Collectors.toList()));
        }

        return ApiResponse.succResult().setData(ordersDepthVo);
    }

    @Override
    public ApiResponse<GetPairExchangeStatistics> getPairExchangeStatistics(String pair) {
        // 获取交易信息
        ConcurrentHashMap<String, CoinTrader> coinTraderMap = new ConcurrentHashMap();
        if (StringUtils.isNotEmpty(pair)) {
            CoinTrader coinTrader = coinTraderFactory.coinTraderMap().get(pair);
            if (coinTrader != null) {
                coinTraderMap.put(pair, coinTrader);
            }
        } else {
            coinTraderMap = coinTraderFactory.coinTraderMap();
        }

        GetPairExchangeStatistics resultVo = new GetPairExchangeStatistics();
        for (Map.Entry<String, CoinTrader> coinTraderEntry : coinTraderMap.entrySet()) {
            Map<String, Object> exchangeInfo = new HashMap<>();
            resultVo.getPairInfo().put(coinTraderEntry.getKey(), exchangeInfo);
            CoinTrader coinTrader = coinTraderEntry.getValue();
            BigDecimal bidTotalAmount = BigDecimal.ZERO;
            Long bidOrderSize = 0L;
            int bidPriceDepth = 0;
            for (Map.Entry<BigDecimal, MergeOrder> depthEntry : coinTrader.getBuyLimitPriceQueue().entrySet()) {
                if(depthEntry.getValue().isValid()){
                    ++bidPriceDepth;
                }
                Iterator<TradeOrder> iterator = depthEntry.getValue().iterator();
                while (iterator.hasNext()) {
                    TradeOrder tradeOrder = iterator.next();
                    bidTotalAmount = bidTotalAmount.add(tradeOrder.getTotalAmount().subtract(tradeOrder.getDealAmount()));
                    bidOrderSize += 1;
                }
            }

            BigDecimal askTotalAmount = BigDecimal.ZERO;
            Long askOrderSize = 0L;
            int askPriceDepth = 0;
            for (Map.Entry<BigDecimal, MergeOrder> depthEntry : coinTrader.getSellLimitPriceQueue().entrySet()) {
                if(depthEntry.getValue().isValid()){
                    ++askPriceDepth;
                }
                Iterator<TradeOrder> iterator = depthEntry.getValue().iterator();
                while (iterator.hasNext()) {
                    TradeOrder tradeOrder = iterator.next();
                    askTotalAmount = askTotalAmount.add(tradeOrder.getTotalAmount().subtract(tradeOrder.getDealAmount()));
                    askOrderSize += 1;
                }
            }
            exchangeInfo.put("idMapSize", coinTrader.getIdTradeOrderMap().size());
            exchangeInfo.put("bidPriceDepth", bidPriceDepth);
            exchangeInfo.put("bidTotalAmount", bidTotalAmount);
            exchangeInfo.put("bidOrderSize", bidOrderSize);
            exchangeInfo.put("askPriceDepth", askPriceDepth);
            exchangeInfo.put("askTotalAmount", askTotalAmount);
            exchangeInfo.put("askOrderSize", askOrderSize);
        }

        return ApiResponse.succResult().setData(resultVo);
    }

    @Override
    public ApiResponse<TradeOrder> findOrderById(String pair, String id) {
        CoinTrader coinTrader = coinTraderFactory.coinTraderMap().get(pair);
        TradeOrder tradeOrder = null;
        if (coinTrader != null) {
            tradeOrder = coinTrader.getIdTradeOrderMap().get(id);
        }
        return ApiResponse.succResult().setData(tradeOrder);
    }

    @Override
    public ApiResponse<GetExchangeMemStatistics> getExchangeMemStatistics() {
        GetExchangeMemStatistics getExchangeMemStatistics = new GetExchangeMemStatistics();
        getExchangeMemStatistics.setKafkaUtilStatistics(exchangeKafkaUtil.getKafkaUtilStatistics());
        getExchangeMemStatistics.setLocalCacheStatistics(localCacheUtil.getLocalCacheStatistics());
        return ApiResponse.succResult().setData(getExchangeMemStatistics);
    }

    @Override
    public ApiResponse<OrdersDepthVo> getQuotationOrdersDepthForMem(String pair, Integer orderSizeAfterMerged, int amountPrecision, int pricePrecision) {
        CoinTrader coinTrader = coinTraderFactory.coinTraderMap().get(pair);
        TreeMap<BigDecimal, BigDecimal> buyPlateQueue = coinTrader.getBuyPlateQueue();
        OrdersDepthVo ordersDepthVo = new OrdersDepthVo();
        if (buyPlateQueue != null && buyPlateQueue.size() > 0) {
            buyPlateQueue.forEach((k, v) -> {
                OrdersDepthItemVo ordersDepthItemVo = new OrdersDepthItemVo();
                ordersDepthItemVo.setPrice(k);
                ordersDepthItemVo.setExchangeAmount(v);
                ordersDepthVo.getBidDepth().add(ordersDepthItemVo);
            });
        }
        TreeMap<BigDecimal, BigDecimal> sellPlateQueue = coinTrader.getSellPlateQueue();
        if (sellPlateQueue != null && sellPlateQueue.size() > 0) {
            sellPlateQueue.forEach((k, v) -> {
                OrdersDepthItemVo ordersDepthItemVo = new OrdersDepthItemVo();
                ordersDepthItemVo.setPrice(k);
                ordersDepthItemVo.setExchangeAmount(v);
                ordersDepthVo.getAskDepth().add(ordersDepthItemVo);
            });
        }
        return ApiResponse.succResult().setData(ordersDepthVo);
    }

    @Override
    public ApiResponse<GetPairExchangeStatistics> getPairMemOrderInfo(String pair) {
        GetPairExchangeStatistics getPairExchangeStatistics = new GetPairExchangeStatistics();
        CoinTrader coinTrader = coinTraderFactory.coinTraderMap().get(pair);

        Map<String, Object> pairInfoMap = new HashMap<>();
        pairInfoMap.put("buyLimitPriceQueue", coinTrader.getBuyLimitPriceQueue());
        pairInfoMap.put("sellLimitPriceQueue", coinTrader.getSellLimitPriceQueue());
        pairInfoMap.put("idTradeOrderMap", coinTrader.getIdTradeOrderMap());
        Map<String, Map<String, Object>> pairInfo = new HashMap<>();
        pairInfo.put(pair, pairInfoMap);
        getPairExchangeStatistics.setPairInfo(pairInfo);
        return ApiResponse.succResult().setData(getPairExchangeStatistics);
    }
}
