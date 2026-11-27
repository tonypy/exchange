package com.weibi.exchange.service;

import com.weibi.config.model.trade.TradeOrder;
import com.weibi.core.commons.model.mybatis.BasePage;
import com.weibi.exchange.model.OrdersDepthDTO;
import com.weibi.exchange.model.TradeOrderUnfinished;

import java.util.List;

/**
 * 挂单表 服务类
 *
 * @author jsong
 * @since 2022-02-07
 */
public interface TradeOrderUnfinishedService {
    /**
     * 统计交易对数量
     *
     * @param userId
     * @param pair
     * @param direction
     * @return
     */
    Integer countExUnfinishedOrderByIdAndPair(String userId, String pair, String direction);

    /**
     * 获取交易对未完成订单
     *
     * @param symbol
     * @return
     */
    List<TradeOrder> findAllTradingOrderBySymbol(String symbol);

    /**
     * 查找订单
     *
     * @param id
     * @return
     */
    TradeOrder findOrderById(String id);

    /**
     * 查询行情深度
     *
     * @param pair    必传
     * @param isBid   必传
     * @param maxSize 必传
     * @return
     */
    List<OrdersDepthDTO> getOrdersDepth(String pair, boolean isBid, int maxSize);

    /**
     * 批量删除订单
     *
     * @param orders
     */
    void batchDeleteTradeOrder(List<TradeOrder> orders);

    /**
     * 批量插入订单
     *
     * @param orders
     */
    void batchInsertTradeOrder(List<TradeOrder> orders);

    /**
     * 批量更新订单
     *
     * @param orders
     */
    void batchUpdateTradeOrder(List<TradeOrder> orders);

    /**
     * @param id
     * @param userId
     * @param pair
     * @param direction
     * @param src
     * @param orderType
     * @param status
     * @param page
     * @param pageSize
     */
    BasePage<TradeOrderUnfinished> getTradeOrderUnfinishedList(
            String id, String userId, String pair, String direction, String src, String orderType,
            Integer status, Integer page, Integer pageSize);

    /**
     * @param ids
     * @param userId
     * @return
     */
    List<TradeOrderUnfinished> getTradeOrderUnfinishedByIdsAndUserId(List<String> ids, String userId);

    /**
     * @param ids
     * @param pair
     * @return
     */
    List<TradeOrderUnfinished> getTradeOrderUnfinishedByIdsAndPair(List<String> ids, String pair);

    /**
     * @param ids
     * @param userId
     * @param pair
     * @return
     */
    List<TradeOrderUnfinished> getTradeOrderUnfinishedByIdsAndUserIdAndPair(List<String> ids, String userId, String pair);

    /**
     * @param userId
     * @return java.util.List<java.lang.String>
     * @author killua
     * @date 2022/10/19 16:20
     */
    List<String> getPairForTradeOrderUnfinished(String userId);
}
