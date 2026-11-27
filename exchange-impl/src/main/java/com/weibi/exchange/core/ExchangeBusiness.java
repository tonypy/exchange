package com.weibi.exchange.core;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.weibi.config.model.trade.TradeOrder;
import com.weibi.core.balance.delega.enums.WriterActionTypeEnum;
import com.weibi.core.balance.delega.model.*;
import com.weibi.core.balance.delega.utils.UserAccountsUtils;
import com.weibi.core.commons.api.ApiResponse;
import com.weibi.core.commons.logging.ILogging;
import com.weibi.core.commons.util.NumberUtil;
import com.weibi.exchange.model.ExchangeResult;
import com.weibi.exchange.service.TradeOrderUnfinishedService;
import com.weibi.exchange.util.BizUtil;
import org.apache.commons.lang3.tuple.MutablePair;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.weibi.core.balance.delega.utils.UserAccountsUtils.*;
import static com.weibi.core.commons.api.CommonRespConstant.RESULT_CODE_AV_NOT_ENOUGH;
import static com.weibi.core.commons.api.CommonRespConstant.RESULT_CODE_FR_NOT_ENOUGH;

@Service
@Primary
public class ExchangeBusiness implements ILogging {
    private static final BigDecimal LEAGENCY_VAL_THRESHOLD = NumberUtil.leastPriceIncr(8);
    private static final String LOCK_ACCOUNTS_SQL = "select * from bkex_exchange.tbl_user_ex_account where id in (:accountIds) for update";
    private static final String LOCK_ACCOUNT_SQL = "select * from bkex_exchange.tbl_user_ex_account where id =? for update;";
    private static final String NO_LOCK_ACCOUNT_SQL = "select * from bkex_exchange.tbl_user_ex_account where id =?;";
    private static final String INSERT_ACCOUNTS = "INSERT INTO bkex_exchange.tbl_user_ex_account (" +
            "id, user_id, wallet_type, coin_type, coin_amount, coin_froze,update_time) " +
            "VALUES (?, ?, ?, ?,?,? ,?) ";
    private static final String UPDATE_ACCOUNTS = "update bkex_exchange.tbl_user_ex_account set coin_amount = ?," +
            "coin_froze=?," +
            "update_time=? " +
            "where id=?";
    private static final String SELECT_ORDERS_BY_SYMBOL = "select * from bkex_exchange.tbl_trade_order_unfinished where pair = :pair ORDER BY created_time";
    private static final String SELECT_ORDER_BY_ID = "select * from bkex_exchange.tbl_trade_order_unfinished where id =?";
    private static final String INSERT_ORDERS = "INSERT INTO bkex_exchange.tbl_trade_order_unfinished (" +
            "id, user_id, pair,direction, src , order_type, deal_amount, total_amount, frozen_amount_by_order, " +
            "total_anchor_coin, price, fee_rate, status, referred_by, created_time, update_time, enable_bkk_discount, wallet_type, operator, stop_price) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String UPDATE_ORDERS = "update bkex_exchange.tbl_trade_order_unfinished set " +
            "deal_amount=?,total_amount=?,frozen_amount_by_order=?,total_anchor_coin= ?,fee_rate =? ,status=?,update_time=? where id=?";
    private static final String DEL_ORDERS = "delete from  bkex_exchange.tbl_trade_order_unfinished where id =?";
    private static final String DEL_ACCOUNTS = "delete from  bkex_exchange.tbl_user_ex_account where id =?";

    @Resource
    private NamedParameterJdbcTemplate bizMasterNamedParamTemplate;
    @Resource
    private JdbcTemplate bizSlaveTemplate;
    @Autowired
    private TradeOrderUnfinishedService tradeOrderUnfinishedService;

    /**
     * 批量处理账务账务
     *
     * @param accounts
     * @param action
     */
    public void batchProcessUserExAccount(List<UserCoinAccount> accounts, String action) {
        if (WriterActionTypeEnum.ACTION_INSERT.getCode().equals(action)) {
            batchInsertUserExAccount(accounts);
        } else if (WriterActionTypeEnum.ACTION_UPDATE.getCode().equals(action)) {
            batchUpdateUserExAccount(accounts);
        } else if (WriterActionTypeEnum.ACTION_DELETE.getCode().equals(action)) {
        }
    }

    /**
     * 批量处理订单
     *
     * @param orders
     * @param action
     */
    public void batchProcessTradeOrder(List<TradeOrder> orders, String action) {
        if (WriterActionTypeEnum.ACTION_INSERT.getCode().equals(action)) {
            tradeOrderUnfinishedService.batchInsertTradeOrder(orders);
        } else if (WriterActionTypeEnum.ACTION_UPDATE.getCode().equals(action)) {
            tradeOrderUnfinishedService.batchUpdateTradeOrder(orders);
        } else if (WriterActionTypeEnum.ACTION_DELETE.getCode().equals(action)) {
            tradeOrderUnfinishedService.batchDeleteTradeOrder(orders);
        }
    }

    public ApiResponse<ExchangeResult> clearingExchangeResult(ExchangeResult exchangeResult) {
        ApiResponse<ExchangeResult> clearingApiResponse = new ApiResponse<>();
        clearingApiResponse.setData(exchangeResult);

        // 清算扣除用户余额 和计算余额变动记录
        if ((exchangeResult.getMatchingResults().size() > 0)
                || exchangeResult.getBalanceChangeReqBeforeMatch() != null) {
            // 撮合完成 处理清算逻辑
            Map<String, AmtChangeInfo> allAmtChangeInfo = UserAccountsUtils.buildAmtChangeInfosFromBalanceRecords(
                    exchangeResult.getAllBalanceChangeRecord());
            // 撮合完成计算账务
            //checkEqualToZeroAndWarnIfNecc(allAmtChangeInfo);

            // 锁定账户
            Map<String, UserCoinAccount> userCoinAccountMap = getAndLockUserCoinAccount(allAmtChangeInfo);
            /*Date nowTime = new Date();
            if (exchangeResult.getBalanceChangeReqBeforeMatch() != null) {
                exchangeResult.getBalanceChangeReqBeforeMatch().setBusinessTime(nowTime);
            }
            exchangeResult.getMatchingResults().forEach(matchingResult -> {
                BalanceChangeRequest matchBalanceChangeRequest = matchingResult.getMatchBalanceChangeRequest();
                if (matchBalanceChangeRequest != null) {
                    matchBalanceChangeRequest.setBusinessTime(nowTime);
                }
            });*/

            // 清算
            Map<String, InterAmtChangeLog> beforeChangeMap = new HashMap<>();
            for (Map.Entry<String, AmtChangeInfo> entry : allAmtChangeInfo.entrySet()) {
                String mainKey = entry.getKey();
                AmtChangeInfo amtChangeInfo = entry.getValue();
                String walletType = walletTypeFromMainKey(mainKey);
                String action = WriterActionTypeEnum.ACTION_INSERT.getCode();
                UserCoinAccount existing = userCoinAccountMap.get(mainKey);
                if (existing == null) {
                    existing = new UserCoinAccount();
                    existing.setId(mainKey);
                    existing.setCoinType(coinTypeFromMainKey(mainKey));
                    existing.setUserId(userIdFromMainKey(mainKey));
                    existing.setCoinFroze(BigDecimal.ZERO);
                    existing.setCoinAmount(BigDecimal.ZERO);
                } else {
                    action = WriterActionTypeEnum.ACTION_UPDATE.getCode();
                }
                BigDecimal originalAv = existing.getCoinAmount();
                BigDecimal originalFr = existing.getCoinFroze();
                String userId = existing.getUserId();
                String coinType = existing.getCoinType();
                BigDecimal afterAv = existing.getCoinAmount().add(amtChangeInfo.getAvailableChange());
                BigDecimal afterFr = existing.getCoinFroze().add(amtChangeInfo.getFrozenChange());
                existing.setCoinAmount(afterAv);
                existing.setCoinFroze(afterFr);
                existing.setWalletType(walletType);
                existing.setUpdateTime(new Timestamp(System.currentTimeMillis()));
                if (existing.getCoinAmount().compareTo(BigDecimal.ZERO) < 0) {
                    clearingApiResponse.setCode(RESULT_CODE_AV_NOT_ENOUGH);
                    clearingApiResponse.setCodeGlobalParams(Lists.newArrayList(coinType, userId, walletType));
                    error("提醒严重错误,撮合清算账务余额不够，应该扣除：<{}>, <{}>,<{}>,<{}>",
                            amtChangeInfo.getAvailableChange(),
                            JSON.toJSONString(existing),
                            JSON.toJSONString(exchangeResult));
                    return clearingApiResponse;
                }
                if (existing.getCoinFroze().compareTo(BigDecimal.ZERO) < 0) {
                    if (meetLeagencyData(existing.getCoinFroze())) {
                        existing.setCoinFroze(BigDecimal.ZERO);
                    } else {
                        clearingApiResponse.setCode(RESULT_CODE_FR_NOT_ENOUGH);
                        clearingApiResponse.setCodeGlobalParams(Lists.newArrayList(coinType, userId, walletType));
                        error("提醒严重错误,撮合清算账务冻结余额不够，应该扣除：<{}>, <{}> ,<{}>,<{}>",
                                amtChangeInfo.getFrozenChange(),
                                JSON.toJSONString(existing),
                                JSON.toJSONString(exchangeResult));
                        return clearingApiResponse;
                    }
                }
                beforeChangeMap.put(mainKey, new InterAmtChangeLog()
                        .setAvBeforeChange(originalAv)
                        .setFrBeforeChange(originalFr)
                        .setAction(action)
                        .setUserCoinAccount(existing));
            }

            // 存储账户变动记录 和 更新二级缓存信息
            exchangeResult.setBalanceChangeHist(beforeChangeMap);
            beforeChangeMap.entrySet().forEach(new Consumer<Map.Entry<String, InterAmtChangeLog>>() {
                @Override
                public void accept(Map.Entry<String, InterAmtChangeLog> entry) {
                    UserCoinAccount account = entry.getValue().getUserCoinAccount();
                    String action = entry.getValue().getAction();
                    TradeExecutor.updateMemLockedAccount(account, action);
                }
            });
        }
        return clearingApiResponse;
    }

    /**
     * 获取单个用户账户 先取内存 没有取数据库
     *
     * @param walletType
     * @param userId
     * @param coinType
     * @return
     */
    public UserCoinAccount getAndLockUserCoinAccountByCache(String walletType, String userId, String coinType) {
        String accountId = BizUtil.buildAccountId(walletType, userId, coinType);
        List<UserCoinAccount> andLockUserCoinAccount = batchGetAndLockUserCoinAccount(Arrays.asList(accountId));
        return CollectionUtils.isEmpty(andLockUserCoinAccount) ? null : andLockUserCoinAccount.get(0);
    }

    /**
     * 获取bkk账户余额
     *
     * @param walletType
     * @param userId
     * @param coinType
     * @return
     */
    public UserCoinAccount getAndLockUserBkkFreeCoinAccountByCache(String walletType, String userId, String coinType) {
        String accountId = BizUtil.buildAccountId(walletType, userId, coinType);
        UserCoinAccount bkkFreeAccounts = TradeExecutor.getLocalTmpBkkFreeAccounts(accountId);
        if (bkkFreeAccounts == null) {
            UserCoinAccount userCoinAccount = getAndLockUserCoinAccountByCache(walletType, userId, coinType);
            if (userCoinAccount != null) {
                bkkFreeAccounts = new UserCoinAccount();
                BeanUtils.copyProperties(userCoinAccount, bkkFreeAccounts);
            }
        }
        return bkkFreeAccounts;
    }

    /**
     * 根据币对获取数据库订单
     *
     * @param symbol
     * @return
     */
    public List<TradeOrder> findAllTradingOrderBySymbol(String symbol) {
        return tradeOrderUnfinishedService.findAllTradingOrderBySymbol(symbol);
    }

    public UserCoinAccount selectExAccountNoLockInMaster(String accountId) {
        List<UserCoinAccount> resultList = bizMasterNamedParamTemplate.getJdbcTemplate().query(NO_LOCK_ACCOUNT_SQL,
                new Object[]{accountId}, new BeanPropertyRowMapper(UserCoinAccount.class));
        UserCoinAccount userCoinAccount = resultList != null && resultList.size() > 0 ? resultList.get(0) : null;
        return userCoinAccount;
    }

    /**
     * 清算平台手续费
     *
     * @param balanceChangeRequests
     * @return
     */
    public boolean clearingPublicFeeBalanceChangeRequest(List<BalanceChangeRequest> balanceChangeRequests) {
        List<BalanceChangeRecord> balanceChangeRecords = new ArrayList<>();
        balanceChangeRequests.forEach(item -> {
            balanceChangeRecords.addAll(item.getChangeRecords());
        });
        if (balanceChangeRecords.isEmpty()) {
            return false;
        }

        Map<String, AmtChangeInfo> allAmtChangeInfo = UserAccountsUtils.buildAmtChangeInfosFromBalanceRecords(
                balanceChangeRecords);

        // 锁定账户
        List<UserCoinAccount> userCoinAccounts = batchSelectExAccountForUpdateInMaster(allAmtChangeInfo.keySet());
        Map<String, UserCoinAccount> userCoinAccountMap = userCoinAccounts.stream().collect(Collectors.toMap(UserCoinAccount::getId, item -> item));
        List<UserCoinAccount> insertAccounts = new ArrayList<>();
        List<UserCoinAccount> updateAccounts = new ArrayList<>();


        // 清算
        Map<String, InterAmtChangeLog> beforeChangeMap = new HashMap<>();
        for (Map.Entry<String, AmtChangeInfo> entry : allAmtChangeInfo.entrySet()) {
            String mainKey = entry.getKey();
            AmtChangeInfo amtChangeInfo = entry.getValue();
            String walletType = walletTypeFromMainKey(mainKey);
            String action = WriterActionTypeEnum.ACTION_INSERT.getCode();
            UserCoinAccount existing = userCoinAccountMap.get(mainKey);
            if (existing == null) {
                existing = new UserCoinAccount();
                existing.setId(mainKey);
                existing.setCoinType(coinTypeFromMainKey(mainKey));
                existing.setUserId(userIdFromMainKey(mainKey));
                existing.setCoinFroze(BigDecimal.ZERO);
                existing.setCoinAmount(BigDecimal.ZERO);
            } else {
                action = WriterActionTypeEnum.ACTION_UPDATE.getCode();
            }
            BigDecimal originalAv = existing.getCoinAmount();
            BigDecimal originalFr = existing.getCoinFroze();
            String userId = existing.getUserId();
            String coinType = existing.getCoinType();
            BigDecimal afterAv = existing.getCoinAmount().add(amtChangeInfo.getAvailableChange());
            BigDecimal afterFr = existing.getCoinFroze().add(amtChangeInfo.getFrozenChange());
            existing.setCoinAmount(afterAv);
            existing.setCoinFroze(afterFr);
            existing.setWalletType(walletType);
            existing.setUpdateTime(new Timestamp(System.currentTimeMillis()));
            if (WriterActionTypeEnum.ACTION_INSERT.getCode().equalsIgnoreCase(action)) {
                insertAccounts.add(existing);
            } else {
                updateAccounts.add(existing);
            }
            beforeChangeMap.put(mainKey, new InterAmtChangeLog()
                    .setAvBeforeChange(originalAv)
                    .setFrBeforeChange(originalFr)
                    .setAction(action)
                    .setUserCoinAccount(existing));
        }

        // 更新数据库
        batchInsertUserExAccount(insertAccounts);
        batchUpdateUserExAccount(updateAccounts);

        // 组装消息信息
        for (BalanceChangeRequest balanceChangeRequest : balanceChangeRequests) {
            balanceChangeRequest.setChangeRecords(UserAccountsUtils.calBalanceChangeLogsAndUpdateHistLogs(
                    balanceChangeRequest.getChangeRecords(), beforeChangeMap));
        }
        return true;
    }

    /**
     * 批量插入账户
     *
     * @param accounts
     */
    private void batchInsertUserExAccount(List<UserCoinAccount> accounts) {
        if (CollectionUtil.isEmpty(accounts)) {
            return;
        }
        bizMasterNamedParamTemplate.getJdbcTemplate().batchUpdate(INSERT_ACCOUNTS, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement st, int i) throws SQLException {
                UserCoinAccount account = accounts.get(i);
                st.setString(1, account.getId());
                st.setString(2, account.getUserId());
                st.setString(3, account.getWalletType());
                st.setString(4, account.getCoinType());
                st.setBigDecimal(5, NumberUtil.getValZeroIfEmpty(account.getCoinAmount()));
                st.setBigDecimal(6, NumberUtil.getValZeroIfEmpty(account.getCoinFroze()));
                st.setTimestamp(7, account.getUpdateTime());
            }

            @Override
            public int getBatchSize() {
                return accounts.size();
            }
        });
    }

    /**
     * 批零更新账户
     *
     * @param accounts
     */
    private void batchUpdateUserExAccount(List<UserCoinAccount> accounts) {
        if (CollectionUtil.isEmpty(accounts)) {
            return;
        }
        bizMasterNamedParamTemplate.getJdbcTemplate().batchUpdate(UPDATE_ACCOUNTS, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement st, int i) throws SQLException {
                UserCoinAccount account = accounts.get(i);
                st.setBigDecimal(1, NumberUtil.getValZeroIfEmpty(account.getCoinAmount()));
                st.setBigDecimal(2, NumberUtil.getValZeroIfEmpty(account.getCoinFroze()));
                st.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
                st.setString(4, account.getId());
            }

            @Override
            public int getBatchSize() {
                return accounts.size();
            }
        });
    }

    /**
     * 批量更新订单
     *
     * @param orders
     */
    private void batchUpdateTradeOrder(List<TradeOrder> orders) {
        if (CollectionUtil.isEmpty(orders)) {
            return;
        }
        bizMasterNamedParamTemplate.getJdbcTemplate().batchUpdate(UPDATE_ORDERS, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement st, int i) throws SQLException {
                TradeOrder order = orders.get(i);
                st.setBigDecimal(1, order.getDealAmount());
                st.setBigDecimal(2, order.getTotalAmount());
                st.setBigDecimal(3, order.getFrozenAmountByOrder());
                st.setBigDecimal(4, order.getTotalAnchorCoin());
                st.setBigDecimal(5, order.getFeeRate());
                st.setInt(6, order.getStatus());
                st.setTimestamp(7, order.getUpdateTime());
                st.setString(8, order.getId());
            }

            @Override
            public int getBatchSize() {
                return orders.size();
            }
        });
    }

    /**
     * 批量插入订单
     *
     * @param orders
     */
    private void batchInsertTradeOrder(List<TradeOrder> orders) {
        if (CollectionUtil.isEmpty(orders)) {
            return;
        }
        bizMasterNamedParamTemplate.getJdbcTemplate().batchUpdate(INSERT_ORDERS, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement st, int i) throws SQLException {
                TradeOrder newOrder = orders.get(i);
                st.setString(1, newOrder.getId());
                st.setString(2, newOrder.getUserId());
                st.setString(3, newOrder.getPair());
                st.setString(4, newOrder.getDirection());
                st.setString(5, newOrder.getSrc());
                st.setString(6, newOrder.getOrderType());
                st.setBigDecimal(7, NumberUtil.getValZeroIfEmpty(newOrder.getDealAmount()));
                st.setBigDecimal(8, newOrder.getTotalAmount());
                st.setBigDecimal(9, NumberUtil.getValZeroIfEmpty(newOrder.getFrozenAmountByOrder()));
                st.setBigDecimal(10, newOrder.getTotalAnchorCoin());
                st.setBigDecimal(11, newOrder.getPrice());
                st.setBigDecimal(12, newOrder.getFeeRate());
                st.setInt(13, newOrder.getStatus());
                st.setString(14, newOrder.getReferredBy());
                st.setTimestamp(15, newOrder.getCreatedTime());
                st.setTimestamp(16, newOrder.getUpdateTime());
                st.setBoolean(17, newOrder.getEnableBkkDiscount());
                st.setString(18, newOrder.getWalletType());
                st.setString(19, newOrder.getOperator());
                st.setBigDecimal(20, newOrder.getStopPrice());
            }

            @Override
            public int getBatchSize() {
                return orders.size();
            }
        });
    }

    /**
     * 批量删除订单
     *
     * @param orders
     */
    private void batchDeleteTradeOrder(List<TradeOrder> orders) {
        if (CollectionUtil.isEmpty(orders)) {
            return;
        }
        bizMasterNamedParamTemplate.getJdbcTemplate().batchUpdate(DEL_ORDERS, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement st, int i) throws SQLException {
                TradeOrder order = orders.get(i);
                st.setString(1, order.getId());
            }

            @Override
            public int getBatchSize() {
                return orders.size();
            }
        });
    }

    /**
     * 检查币对处长和入账是否正确
     *
     * @param accumulatedAccountInfos
     */
    private void checkEqualToZeroAndWarnIfNecc(Map<String, AmtChangeInfo> accumulatedAccountInfos) {
        //1. 先按照币种做groupBy， 做一次币种总额变动是否为0的检查，但是只做检查和日志告知，不阻断
        Map<String, MutablePair<BigDecimal, BigDecimal>> coinAmtChange = new HashMap<>();

        for (Map.Entry<String, AmtChangeInfo> entry : accumulatedAccountInfos.entrySet()) {
            String mainKey = entry.getKey();
            String coinType = coinTypeFromMainKey(mainKey);
            MutablePair<BigDecimal, BigDecimal> pairAvFrChange = coinAmtChange.getOrDefault(coinType, new MutablePair<>(BigDecimal.ZERO, BigDecimal.ZERO));
            BigDecimal avChange = entry.getValue().getAvailableChange();
            BigDecimal frChange = entry.getValue().getFrozenChange();
            pairAvFrChange.setLeft(pairAvFrChange.getLeft().add(avChange));
            pairAvFrChange.setRight(pairAvFrChange.getRight().add(frChange));
            coinAmtChange.put(coinType, pairAvFrChange);
        }

        coinAmtChange.entrySet().forEach(new Consumer<Map.Entry<String, MutablePair<BigDecimal, BigDecimal>>>() {
            @Override
            public void accept(Map.Entry<String, MutablePair<BigDecimal, BigDecimal>> entry) {
                MutablePair<BigDecimal, BigDecimal> pairCoinAvFrChange = entry.getValue();
                BigDecimal totalCoinAvFrChange = pairCoinAvFrChange.getLeft().abs().subtract(pairCoinAvFrChange.getRight().abs());
                if (totalCoinAvFrChange.compareTo(BigDecimal.ZERO) != 0) {
                    error("本次撮合账务计算出现异常,币种 <{}>, 总共 avChange<{}>, frChange<{}>", entry.getKey(), pairCoinAvFrChange.getLeft(), pairCoinAvFrChange.getRight());
                }
            }
        });
    }

    /***
     * 检查精度
     * @param valAfterChange
     * @return
     */
    private boolean meetLeagencyData(BigDecimal valAfterChange) {
        return valAfterChange.abs().compareTo(LEAGENCY_VAL_THRESHOLD) <= 0;
    }

    /**
     * 根据撮合结果批量锁定账户 每批次8个
     *
     * @param allAmtChangeInfo
     * @return
     */
    private Map<String, UserCoinAccount> getAndLockUserCoinAccount(Map<String, AmtChangeInfo> allAmtChangeInfo) {
        List<UserCoinAccount> userCoinAccounts = new ArrayList<>();
        List<String> accountIds = new ArrayList<>();
        for (Map.Entry<String, AmtChangeInfo> entry : allAmtChangeInfo.entrySet()) {
            accountIds.add(entry.getKey());
            if (accountIds.size() >= 8) {
                List<UserCoinAccount> andLockUserCoinAccount = batchGetAndLockUserCoinAccount(accountIds);
                if (!CollectionUtils.isEmpty(andLockUserCoinAccount)) {
                    userCoinAccounts.addAll(andLockUserCoinAccount);
                }
                accountIds.clear();
            }
        }
        if (accountIds.size() > 0) {
            List<UserCoinAccount> andLockUserCoinAccount = batchGetAndLockUserCoinAccount(accountIds);
            if (!CollectionUtils.isEmpty(andLockUserCoinAccount)) {
                userCoinAccounts.addAll(andLockUserCoinAccount);
            }
        }
        return userCoinAccounts.stream().collect(Collectors.toMap(UserCoinAccount::getId, item -> item));
    }

    /**
     * 从内存中批量获取账户 如果没有则从 数据库获取
     *
     * @param accountIds
     * @return
     */
    private List<UserCoinAccount> batchGetAndLockUserCoinAccount(List<String> accountIds) {
        List<UserCoinAccount> resultList = new ArrayList<>();
        List<String> accountIdDbList = new ArrayList<>();
        accountIds.forEach(accountId -> {
            UserCoinAccount userCoinAccount = TradeExecutor.getTmpMemLockedAccount(accountId);
            if (userCoinAccount != null) {
                resultList.add(userCoinAccount);
            } else {
                accountIdDbList.add(accountId);
            }
        });
        if (accountIdDbList.size() > 0) {
            List<UserCoinAccount> userCoinAccounts = batchSelectExAccountForUpdateInMaster(accountIdDbList);
            if (!CollectionUtils.isEmpty(userCoinAccounts)) {
                userCoinAccounts.forEach(item -> {
                    resultList.add(item);
                    TradeExecutor.updateTmpMemLockedAccount(item);
                    TradeExecutor.updateLocalDbLockedAccounts(item);
                });
            }
        }
        return resultList;
    }

    /**
     * 从数据库批量锁定获取账户
     *
     * @param accountIds
     * @return
     */
    private List<UserCoinAccount> batchSelectExAccountForUpdateInMaster(Collection<String> accountIds) {
        long startTime = System.currentTimeMillis();
        Connection connection = DataSourceUtils.getConnection(bizMasterNamedParamTemplate.getJdbcTemplate().getDataSource());
        List<UserCoinAccount> resultList = null;
        try {
            Map<String, Object> param = new HashMap<>();
            param.put("accountIds", accountIds);
            resultList = bizMasterNamedParamTemplate.query(LOCK_ACCOUNTS_SQL,
                    param,
                    new BeanPropertyRowMapper(UserCoinAccount.class));
            return resultList;
        } finally {
            BizUtil.ACCOUNT_WARN_LOG.warn("load acc to db:accId-<{}>dbresult-<{}>con-<{}>txid-<{}>time-<{}>",
                    JSON.toJSONString(accountIds),
                    JSON.toJSONString(resultList != null ? resultList : "{}"),
                    connection,
                    BizUtil.atomicLong(),
                    (System.currentTimeMillis() - startTime));
        }
    }
}
