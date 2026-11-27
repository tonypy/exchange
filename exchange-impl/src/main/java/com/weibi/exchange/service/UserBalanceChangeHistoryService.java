package com.weibi.exchange.service;

import com.weibi.core.balance.delega.model.BalanceChangeRequest;

/**
 * 交易余额记录 只用于生成表结构 真实记录在ss库 服务类
 *
 * @author jsong
 * @since 2022-02-10
 */
public interface UserBalanceChangeHistoryService {
    /**
     * 写入账单记录
     * @param request
     * @param responseCode
     * @param fullFailMsg
     * @return
     */
    boolean logBalanceRecordsHistory(BalanceChangeRequest request, int responseCode, String fullFailMsg);
}
