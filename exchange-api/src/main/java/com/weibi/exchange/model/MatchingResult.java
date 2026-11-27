package com.weibi.exchange.model;

import com.weibi.config.model.trade.TradeDeal;
import com.weibi.config.model.trade.TradeOrder;
import com.weibi.core.balance.delega.model.BalanceChangeRecord;
import com.weibi.core.balance.delega.model.BalanceChangeRequest;
import com.weibi.core.commons.BaseToString;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

import static com.weibi.core.commons.api.CommonRespConstant.DEFAULT_ERROR_CODE;
import static com.weibi.core.commons.api.CommonRespConstant.RESULT_SUCCESS;

@Data
public class MatchingResult extends BaseToString {
    private int code;
    private TradeDeal tradeDeal;
    private TradeOrder newBidOrder;
    private TradeOrder newAskOrder;
    private List<BalanceChangeRecord> publicRealFeeRecords = new ArrayList<>();
    private BalanceChangeRequest matchBalanceChangeRequest;
    //private List<String> codeGlobalParams = new ArrayList<>();
    //private MatchingExchangeInfo askExchangeInfo;
    //private BalanceChangeRecord askBkkFeeRecord;
    //private BalanceChangeRecord askBkkPublicFeeRecord;
    //private MatchingExchangeInfo bidExchangeInfo;
    //private BalanceChangeRecord bidBkkFeeRecord;
    //private BalanceChangeRecord bidBkkPublicFeeRecord;


    public static MatchingResult failResult() {
        return failResult(DEFAULT_ERROR_CODE);
    }

    public static MatchingResult failResult(int code) {
        MatchingResult result = new MatchingResult();
        result.setCode(code);
        return result;
    }

    public static MatchingResult succResult() {
        MatchingResult result = new MatchingResult();
        result.setCode(RESULT_SUCCESS);
        return result;
    }

    public boolean successful() {
        return this.code == RESULT_SUCCESS;
    }
}