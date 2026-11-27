package com.weibi.exchange.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.shaded.com.google.common.collect.Lists;
import com.weibi.core.balance.delega.enums.BalanceChangeTypeEnum;
import com.weibi.core.balance.delega.model.BalanceChangeRecord;
import com.weibi.core.balance.delega.model.BalanceChangeRequest;
import com.weibi.core.balance.delega.service.TransProcessDelegationService;
import com.weibi.core.commons.db.extension.service.impl.BaseServiceImpl;
import com.weibi.core.commons.logging.ILogging;
import com.weibi.core.commons.util.CommonResponseUtil;
import com.weibi.core.commons.util.IdGenerator;
import com.weibi.exchange.config.KafkaConfig;
import com.weibi.exchange.entity.UserBalanceChangeHistoryEntity;
import com.weibi.exchange.mapper.UserBalanceChangeHistoryMapper;
import com.weibi.exchange.service.UserBalanceChangeHistoryService;
import com.weibi.exchange.util.LocalCacheUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 交易余额记录 只用于生成表结构 真实记录在ss库 服务实现类
 *
 * @author jsong
 * @since 2022-02-10
 */
@Service
@Primary
public class UserBalanceChangeHistoryServiceImpl extends BaseServiceImpl<UserBalanceChangeHistoryEntity> implements UserBalanceChangeHistoryService, ILogging {

    private static List<String> NO_NEED_BILL_TYPES = Lists.newArrayList(BalanceChangeTypeEnum.CREATE_TRADE.getCode(), BalanceChangeTypeEnum.CANCEL_TRADE.getCode());
    //private static List<String> NO_NEED_BILL_TYPES = new ArrayList<>();

    @Autowired
    private UserBalanceChangeHistoryMapper userBalanceChangeHistoryMapper;
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    private TransProcessDelegationService transDealDelegation;
    @Autowired
    private LocalCacheUtil localCacheUtil;

    @Override
    public boolean logBalanceRecordsHistory(BalanceChangeRequest request, int responseCode, String fullFailMsg) {
        AtomicBoolean encounterException = new AtomicBoolean(false);
        boolean success = CommonResponseUtil.isSuccess(responseCode);
        if (!success) {
            UserBalanceChangeHistoryEntity entity = new UserBalanceChangeHistoryEntity();
            entity.setBusinessType(request.getBusinessType());
            entity.setBusinessKey(request.getBusinessKey());
            entity.setBusinessTime(request.getBusinessTime());
            entity.setUserId("-");
            entity.setCoinType("-");
            entity.setAvailableAmountChange(BigDecimal.ZERO);
            entity.setFrozenAmountChange(BigDecimal.ZERO);
            entity.setUserRole("-");
            entity.setWalletType("-");
            entity.setResultCode(fullFailMsg);
            String id = IdGenerator.generateKey(request.getBusinessTime(), request.getBusinessKey()).toString();
            entity.setId(id);
            try {
                userBalanceChangeHistoryMapper.insert(entity);
            } catch (DuplicateKeyException e) {
                warn("账务记录手续费记录遇到幂等异常，不会抛出异常以免造成消息不断重复消费,record-<{}>", request, e);
                encounterException.set(true);
            }
        } else {
            /*
             * 如果客户端不知道发送请求时候的时候，不知道自己的余额变更情况，则这里就不记录 费用变更流水
             * 则交由 服务端主动发送 余额 变更结果 流水处理 - TOPIC_BALANCE_RES_INIT_FROM_MATCH_ENGINE
             */
            //String users = siteConfService.getConf(TRANS + JOINER_BIZ_TYPE + KEY_USER_NO_NEED_BILL, JSON.toJSONString(Lists.newArrayList()));
            List<String> platFormUser = localCacheUtil.getPlatFormUser();
            String businessType = request.getBusinessType();
            request.getChangeRecords().forEach(new Consumer<BalanceChangeRecord>() {
                @Override
                public void accept(BalanceChangeRecord record) {
                    // 平台账户下撤单不记录账单
                    if (platFormUser.contains(record.getUserId()) && NO_NEED_BILL_TYPES.contains(businessType)) {
                        return;
                    } else {
                        recordSuccRecord(record, request, fullFailMsg, encounterException);
                    }
                }
            });
        }

        boolean matchConcerned = transDealDelegation.bizTypesTakingOverDetails().contains(request.getBusinessType());
        if (!matchConcerned && success) {
            try {
                if (!CollectionUtils.isEmpty(request.getChangeRecords())) {
                    kafkaTemplate.send(KafkaConfig.TOPIC_SUCC_BALANCE_HIST_WRITTEN, JSON.toJSONString(request));
                }
            } catch (Exception e) {
                error("记录到数据中心失败,<{}>", request, e);
            }
        }

        return encounterException.get();
    }

    private void recordSuccRecord(BalanceChangeRecord record, BalanceChangeRequest request, String fullFailMsg, AtomicBoolean encounterException) {
        UserBalanceChangeHistoryEntity entity = new UserBalanceChangeHistoryEntity();
        entity.setBusinessType(request.getBusinessType());
        entity.setBusinessKey(request.getBusinessKey());
        entity.setBusinessTime(request.getBusinessTime());
        entity.setUserId(record.getUserId());
        entity.setWalletType(record.getWalletType());
        entity.setCoinType(record.getCoinType());
        entity.setAvailableAmountChange(record.getAvailableBalanceChange());
        entity.setFrozenAmountChange(record.getFrozenBalanceChange());
        entity.setUserRole(record.getUserRole());
        entity.setAvailableAfterChange(record.getAvAfterChange());
        entity.setFrozenAfterChange(record.getFrAfterChange());
        entity.setTransSequence(record.getTransSequence());
        entity.setRemark(record.getRemark());
        entity.setResultCode(fullFailMsg);
        try{
            String id = IdGenerator.generateKey(request.getBusinessTime(), record.getUserId()).toString();
            entity.setId(id);
        }catch (Exception e){
            entity.setId(IdGenerator.generateKey(String.valueOf(request.getBusinessTime())).toString());
        }
        try {
            userBalanceChangeHistoryMapper.insert(entity);
            warn("recordSuccRecord写入账单DB:<{}>", JSON.toJSONString(entity));
        } catch (DuplicateKeyException e) {
            warn("账务记录手续费记录遇到幂等异常，不会抛出异常以免造成消息不断重复消费,record-<{}>", record, e);
            encounterException.set(true);
        }
    }
}
