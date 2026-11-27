package com.weibi.exchange.service.impl;

import com.weibi.config.api.TradePriceService;
import com.weibi.config.enums.CoinTypeEnum;
import com.weibi.config.enums.TradeDirectionEnum;
import com.weibi.config.model.TradePair;
import com.weibi.config.model.trade.TradeDeal;
import com.weibi.core.commons.db.extension.service.impl.BaseServiceImpl;
import com.weibi.core.commons.logging.ILogging;
import com.weibi.core.commons.util.IdGenerator;
import com.weibi.exchange.entity.TradeStatisticsFeeEntity;
import com.weibi.exchange.mapper.TradeStatisticsFeeMapper;
import com.weibi.exchange.model.TradeStatisticsFee;
import com.weibi.exchange.service.TradeStatisticsFeeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.util.Date;

/**
 * 交易手续费明细记录表 服务实现类
 *
 * @author jsong
 * @since 2022-08-14
 */
@Service
@Primary
public class TradeStatisticsFeeServiceImpl extends BaseServiceImpl<TradeStatisticsFeeEntity> implements TradeStatisticsFeeService, ILogging {
    @Autowired
    private TradeStatisticsFeeMapper tradeStatisticsFeeMapper;
    @Autowired
    private TradePriceService tradePriceService;


    @Override
    public boolean createFeeRecordByDeal(TradeDeal deal) {
        TradeStatisticsFee buyerFeeRecord = convertToFeeRecord(deal, true);
        TradeStatisticsFee sellerFeeRecord = convertToFeeRecord(deal, false);
        this.createFeeRecordIfNotZero(buyerFeeRecord);
        this.createFeeRecordIfNotZero(sellerFeeRecord);
        return false;
    }

    private boolean createFeeRecordIfNotZero(TradeStatisticsFee buyerFeeRecord) {
        try {
            String recordId = IdGenerator.generateKey(buyerFeeRecord.getUserId()).toString();
            buyerFeeRecord.setId(recordId);
            TradeStatisticsFeeEntity entity = fromModelToEntity(buyerFeeRecord);
            if (entity.getFeeUsdtAmount() == null || entity.getFeeUsdtAmount().compareTo(BigDecimal.ZERO) == 0) {
                return true;
            }
            int insertedNo = tradeStatisticsFeeMapper.insert(entity);
            if (insertedNo == 1) {
                return true;
            } else {
                warn("插入手续费记录异常，数据库收影响记录数据条数不为1,{}", buyerFeeRecord);
                return false;
            }
        } catch (Exception e) {
            error("插入手续费记录遇到幂等错误,{}", buyerFeeRecord, e);
            return false;
        }
    }

    private TradeStatisticsFee fromEntityToModel(TradeStatisticsFeeEntity entity) {
        TradeStatisticsFee model = new TradeStatisticsFee();
        BeanUtils.copyProperties(entity, model);
        return model;
    }

    private TradeStatisticsFeeEntity fromModelToEntity(TradeStatisticsFee model) {
        TradeStatisticsFeeEntity entity = new TradeStatisticsFeeEntity();
        BeanUtils.copyProperties(model, entity);
        return entity;
    }

    private TradeStatisticsFee convertToFeeRecord(TradeDeal deal, boolean isBuyeer) {
        TradePair pair = new TradePair(deal.getPair());
        TradeStatisticsFee feeRecord = new TradeStatisticsFee();

        feeRecord.setDealId(deal.getId());
        feeRecord.setDealDirection(deal.getDealDirection());
        feeRecord.setTransferStatus(0); //"还未转账"
        feeRecord.setPair(pair.toString());
        feeRecord.setDealTime(new Timestamp(deal.getCreatedTime().getTime()));
        Date sysDateNow = new Date();
        feeRecord.setCreatedTime(new Timestamp(sysDateNow.getTime()));
        feeRecord.setUpdateTime(new Timestamp(sysDateNow.getTime()));
        feeRecord.setFeeUsdtPrice(deal.getPrice());

        //Date miningDealTime = MiningDate.systemDateTimeToMiningDateTime(feeRecord.getDealTime());
        Date miningDate = dateTimeToDate(feeRecord.getDealTime());
        if (isBuyeer) {
            feeRecord.setFeeCoinAmount(deal.getBuyerFee());
            feeRecord.setFeeCoinType(deal.getBuyerFeeCoinType());
            feeRecord.setOrderDirection(TradeDirectionEnum.BID.getCode());
            feeRecord.setUserId(deal.getBuyerId());
            //feeRecord.setReferredBy(deal.getBuyerReferredBy());
            feeRecord.setOrderSrc(deal.getBuyerSrc());
            feeRecord.setMiningDate(new Timestamp(miningDate.getTime()));
        } else {
            feeRecord.setFeeCoinAmount(deal.getSellerFee());
            feeRecord.setFeeCoinType(deal.getSellerFeeCoinType());
            feeRecord.setOrderDirection(TradeDirectionEnum.ASK.getCode());
            feeRecord.setUserId(deal.getSellerId());
            //feeRecord.setReferredBy(deal.getSellerReferredBy());
            feeRecord.setOrderSrc(deal.getSellerSrc());
            feeRecord.setMiningDate(new Timestamp(miningDate.getTime()));
        }
        if (CoinTypeEnum.USDT.getCode().equals(feeRecord.getFeeCoinType())) {
            feeRecord.setFeeUsdtPrice(tradePriceService.getCurrentUsdtPriceForCoin(feeRecord.getFeeCoinType()));
            feeRecord.setFeeUsdtAmount(feeRecord.getFeeCoinAmount());
        } else if (CoinTypeEnum.BKK.getCode().equals(feeRecord.getFeeCoinType())) {
            feeRecord.setFeeUsdtPrice(tradePriceService.getCurrentUsdtPriceForCoin(feeRecord.getFeeCoinType()));
            feeRecord.setFeeUsdtAmount(feeRecord.getFeeCoinAmount().multiply(feeRecord.getFeeUsdtPrice()));
        } else if (feeRecord.getPair().endsWith(CoinTypeEnum.USDT.getCode())) {
            feeRecord.setFeeUsdtAmount(feeRecord.getFeeCoinAmount().multiply(deal.getPrice()));
        } else {
            feeRecord.setFeeUsdtPrice(tradePriceService.getCurrentUsdtPriceForCoin(feeRecord.getFeeCoinType()));
            feeRecord.setFeeUsdtAmount(feeRecord.getFeeCoinAmount().multiply(feeRecord.getFeeUsdtPrice()));
        }

        return feeRecord;
    }

    private static Date dateTimeToDate(Date time) {
        return Date.from(time.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
