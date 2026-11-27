package com.weibi.exchange.core;

import com.google.common.base.Joiner;
import com.weibi.config.model.trade.TradeDeal;
import com.weibi.config.model.trade.TradeOrder;
import com.weibi.core.balance.delega.model.BalanceChangeRequest;
import com.weibi.core.datacenter.model.EsFinishedOrderModel;
import com.weibi.core.datacenter.model.EsTradeDealModel;
import com.weibi.core.datacenter.model.EsUserBalanceHistoryModel;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class ModelConvertor {

    public static EsFinishedOrderModel fromFinishedOrderToEsModel(TradeOrder order) {
        EsFinishedOrderModel finishedOrder = new EsFinishedOrderModel();
        BeanUtils.copyProperties(order, finishedOrder);
        return finishedOrder;
    }

    public static TradeOrder fromEsModelToFinishedOrder(EsFinishedOrderModel model) {
        TradeOrder finishedOrder = new TradeOrder();
        BeanUtils.copyProperties(model, finishedOrder);
        finishedOrder.setCreatedTime(new Timestamp(model.getCreatedTime().getTime()));
        finishedOrder.setUpdateTime(new Timestamp(model.getUpdateTime().getTime()));
        return finishedOrder;
    }

    public static EsTradeDealModel fromDealToEsModels(TradeDeal deal) {
        EsTradeDealModel esDealModel = new EsTradeDealModel();
        BeanUtils.copyProperties(deal, esDealModel);

        esDealModel.setDealVolume(deal.getPrice().multiply(deal.getDealAmount()));

        return esDealModel;
    }

    public static TradeDeal fromEsModelToDeal(EsTradeDealModel dealModel) {
        TradeDeal tradeDeal = new TradeDeal();
        BeanUtils.copyProperties(dealModel, tradeDeal);

        return tradeDeal;
    }

    public static List<EsUserBalanceHistoryModel> fromReqToEsModels(BalanceChangeRequest directReq) {
        List<EsUserBalanceHistoryModel> esModels = new ArrayList<>();
        directReq.getChangeRecords().forEach(balanceChangeRecord -> {
            EsUserBalanceHistoryModel model = new EsUserBalanceHistoryModel();
            model.setBusinessType(directReq.getBusinessType());
            model.setBusinessKey(directReq.getBusinessKey());
            model.setBusinessTime(directReq.getBusinessTime());

            model.setAvChange(balanceChangeRecord.getAvailableBalanceChange());
            model.setFrChange(balanceChangeRecord.getFrozenBalanceChange());
            model.setUserId(balanceChangeRecord.getUserId());
            model.setCoinType(balanceChangeRecord.getCoinType());
            model.setUserRole(balanceChangeRecord.getUserRole());
            model.setWalletType(balanceChangeRecord.getWalletType());
            String userRole = model.getUserRole();
            userRole = StringUtils.isEmpty(userRole) ? "" : userRole;
            String idInEs = Joiner.on(".").join(model.getBusinessType(), model.getBusinessKey(), model.getUserId(), model.getCoinType(), userRole);
            model.setId(idInEs);

            model.setFrAfterChange(balanceChangeRecord.getFrAfterChange());
            model.setAvAfterChange(balanceChangeRecord.getAvAfterChange());
            model.setTransSeq(balanceChangeRecord.getTransSequence());
            model.setRemark(balanceChangeRecord.getRemark());

            esModels.add(model);
        });
        return esModels;
    }

}
