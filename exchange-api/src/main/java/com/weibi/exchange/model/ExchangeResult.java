package com.weibi.exchange.model;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.IdUtil;
import com.weibi.config.model.trade.TradeOrder;
import com.weibi.core.balance.delega.enums.BalanceChangeTypeEnum;
import com.weibi.core.balance.delega.model.BalanceChangeRecord;
import com.weibi.core.balance.delega.model.BalanceChangeRequest;
import com.weibi.core.balance.delega.model.InterAmtChangeLog;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.weibi.core.commons.api.CommonRespConstant.DEFAULT_ERROR_CODE;
import static com.weibi.core.commons.api.CommonRespConstant.RESULT_SUCCESS;

/**
 * 交易结果类
 *
 * @author jsong
 */
@Data
public class ExchangeResult {
    private int code;
    private String action;
    private String traceId;
    private List<String> codeGlobalParams = new ArrayList<>();
    /***处理之前订单 方便失败回复***/
    private TradeOrder oldFocusedOrder;
    /***处理之后订单***/
    private TradeOrder newFocusedOrder;
    /***撮合下单冻结账户信息***/
    private BalanceChangeRequest balanceChangeReqBeforeMatch;
    /***新增订单-tbl_trade_order_unfinished***/
    private List<TradeOrder> bizOrderAdded = new ArrayList<>();
    /***更新订单-tbl_trade_order_unfinished***/
    private List<TradeOrder> bizOrderUpdated = new ArrayList<>();
    /***删除订单-tbl_trade_order_unfinished***/
    private List<TradeOrder> bizOrderDeleted = new ArrayList<>();
    /***删除订单-tbl_trade_order_finished/tbl_trade_order_finished 焦点单直接成交或失败***/
    private List<TradeOrder> dataOrderDeleted = new ArrayList<>();
    /***单个订单撮合结果***/
    private List<MatchingResult> matchingResults = new ArrayList<>();
    /***撮合 单个账户扣除前和扣除后余额***/
    private Map<String, InterAmtChangeLog> balanceChangeHist = new HashMap<>();

    public static ExchangeResult succResult(BalanceChangeTypeEnum action) {
        ExchangeResult result = new ExchangeResult();
        result.setCode(RESULT_SUCCESS);
        result.setAction(action.getCode());
        result.setTraceId(IdUtil.fastSimpleUUID());
        return result;
    }

    public static ExchangeResult failResult(BalanceChangeTypeEnum action) {
        ExchangeResult result = new ExchangeResult();
        result.setCode(DEFAULT_ERROR_CODE);
        result.setAction(action.getCode());
        result.setTraceId(IdUtil.fastSimpleUUID());
        return result;
    }

    public List<BalanceChangeRecord> getAllBalanceChangeRecord() {
        List<BalanceChangeRecord> allBalanceRecords = new ArrayList<>();
        if (balanceChangeReqBeforeMatch != null) {
            allBalanceRecords.addAll(balanceChangeReqBeforeMatch.getChangeRecords());
        }
        matchingResults.forEach(matchingResult -> {
            BalanceChangeRequest matchBalanceChangeRequest = matchingResult.getMatchBalanceChangeRequest();
            if (matchBalanceChangeRequest != null) {
                allBalanceRecords.addAll(matchBalanceChangeRequest.getChangeRecords());
            }
        });
        return allBalanceRecords;
    }

    public void addBizOrderFilledOrUpdatedOrAdded(TradeOrder tradeOrder, boolean isfocusedOrder) {
        if (tradeOrder.isStatusEnd()) {
            if (!isfocusedOrder) {
                bizOrderDeleted.add(tradeOrder);
            }
            dataOrderDeleted.add(tradeOrder);
        } else if (isfocusedOrder) {
            bizOrderAdded.add(tradeOrder);
        } else {
            bizOrderUpdated.add(tradeOrder);
        }
    }

    public boolean successful() {
        return this.code == RESULT_SUCCESS;
    }

    public void setOldFocusedOrder(TradeOrder tradeOrder) {
        this.oldFocusedOrder = Convert.convert(TradeOrder.class, tradeOrder);
    }
}

