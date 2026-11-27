package com.weibi.exchange.core;

import com.weibi.config.model.trade.TradeOrder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TradeOrderCmd {
    private TradeOrder tradeOrder;
    private String action;
    private Integer partition;
    private Long offset;

    public TradeOrderCmd(TradeOrder tradeOrder, String action) {
        this.tradeOrder = tradeOrder;
        this.action = action;
    }
}
