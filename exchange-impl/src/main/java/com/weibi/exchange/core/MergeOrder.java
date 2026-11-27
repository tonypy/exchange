package com.weibi.exchange.core;

import com.weibi.config.model.trade.TradeOrder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 价格订单队列
 *
 * @author jsong
 */
public class MergeOrder {
    private List<TradeOrder> orders = new ArrayList<>();

    public void add(TradeOrder order) {
        orders.add(order);
    }

    public boolean isValid() {
        return orders.size() == 0 ? false : true;
    }

    public int size() {
        return orders.size();
    }

    public BigDecimal getPrice() {
        return orders.isEmpty() ? BigDecimal.ZERO : orders.get(0).getPrice();
    }

    public Iterator<TradeOrder> iterator() {
        return orders.iterator();
    }

    public BigDecimal getTotalAmount() {
        BigDecimal total = new BigDecimal(0);
        for (TradeOrder item : orders) {
            total = total.add(item.getTotalAmount());
        }
        return total;
    }
}
