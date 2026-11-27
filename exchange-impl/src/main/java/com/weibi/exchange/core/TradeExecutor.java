package com.weibi.exchange.core;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSON;
import com.weibi.config.model.trade.TradeOrder;
import com.weibi.core.balance.delega.enums.BalanceChangeTypeEnum;
import com.weibi.core.balance.delega.enums.WriterActionTypeEnum;
import com.weibi.core.balance.delega.model.AmtChangeInfo;
import com.weibi.core.balance.delega.model.BalanceChangeRecord;
import com.weibi.core.balance.delega.model.UserCoinAccount;
import com.weibi.core.balance.delega.utils.UserAccountsUtils;
import com.weibi.core.commons.api.ApiResponse;
import com.weibi.core.commons.logging.ILogging;
import com.weibi.exchange.model.ExchangeResult;
import com.weibi.exchange.util.BizUtil;
import com.weibi.exchange.util.ExchangeKafkaUtil;
import com.weibi.exchange.util.LocalCacheUtil;
import com.weibi.exchange.util.MetricsUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.shardingsphere.api.hint.HintManager;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StopWatch;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;

import static com.weibi.exchange.util.MetricsUtil.SINGLE_PROCESS_ORDER_TPS_RT;

/**
 * 交易执行器
 *
 * @author jsong
 */
@Slf4j
@Component
public class TradeExecutor implements ILogging {
    /***执行时间超长打印日志 秒***/
    private static final int STOP_WATCH_AND_PRETTY_PRINT_TIME_S = 2;
    /***线程缓存账户 一级缓存 处理完成一次交易进行清除***/
    private static final ThreadLocal<Map<String, UserCoinAccount>> LOCAL_TMP_ACCOUNTS = ThreadLocal.withInitial(HashMap::new);
    /***线程缓存账户 二级缓存 处理完成一批交易进行清除***/
    private static final ThreadLocal<Map<String, Pair<String, UserCoinAccount>>> LOCAL_LOCKED_ACCOUNTS = ThreadLocal.withInitial(HashMap::new);
    /***线程缓存账户 当前批次交易中需要新增的账户***/
    private static final ThreadLocal<Set<String>> LOCAL_INSERT_ACCOUNTS = ThreadLocal.withInitial(HashSet::new);
    /***线程缓存账户 数据库初始账户 记录日志校验使用 进行清除***/
    private static final ThreadLocal<Map<String, UserCoinAccount>> LOCAL_DB_LOCKED_ACCOUNTS = ThreadLocal.withInitial(HashMap::new);
    /***线程缓存账户 BKK手续费临时缓存 进行清除***/
    private static final ThreadLocal<Map<String, UserCoinAccount>> LOCAL_TMP_BKK_FREE_ACCOUNTS = ThreadLocal.withInitial(HashMap::new);

    /***线程缓存订单 用户持久化到数据库***/
    private static final ThreadLocal<Map<String, Pair<String, TradeOrder>>> LOCAL_TRADE_ORDER = ThreadLocal.withInitial(HashMap::new);
    /***线程缓存订单 计划委托 更新redis***/
    private static final ThreadLocal<Map<String, Pair<String, TradeOrder>>> LOCAL_STOP_LIMIT_TRADE_ORDER = ThreadLocal.withInitial(HashMap::new);
    /***线程缓存订单 当前批次插入订单***/
    private static final ThreadLocal<Set<String>> LOCAL_INSERT_TRADE_ORDER = ThreadLocal.withInitial(HashSet::new);
    /***线程缓存订单 当前批次删除订单***/
    private static final ThreadLocal<Set<String>> LOCAL_DELETE_TRADE_ORDER = ThreadLocal.withInitial(HashSet::new);

    /***线程缓存交易对配置 用户异常恢复交易对队列***/
    private static final ThreadLocal<Map<String, CoinTrader>> LOCAL_COIN_TRADER = ThreadLocal.withInitial(HashMap::new);
    /***线程缓存交交易结果 用于推送或其他业务处理***/
    private static final ThreadLocal<List<ExchangeResult>> LOCAL_EXCHANGE_RESULT = ThreadLocal.withInitial(ArrayList::new);
    /***市价单余额本地线程变量***/
    private static final ThreadLocal<BigDecimal> LOCAL_CURRENT_REDUCECOINAV = ThreadLocal.withInitial(() -> BigDecimal.ZERO);

    /***线程任务执行器***/
    private static final ThreadLocal<StopWatch> LOCAL_STOP_WATCH = new ThreadLocal<>();

    @Resource
    private NamedParameterJdbcTemplate bizMasterNamedParamTemplate;
    @Resource
    private TransactionTemplate requiredTransactionTemplate;
    @Autowired
    private CoinTraderFactory coinTraderFactory;
    @Autowired
    private ExchangeKafkaUtil exchangeKafkaUtil;
    @Autowired
    private ExchangeBusiness exchangeBusiness;
    @Autowired
    private LocalCacheUtil localCacheUtil;
    @Autowired
    private MetricsUtil metricsUtil;

    /**
     * 1、一次处理一批订单 如果报错整批订单回滚撤掉  清空交易对撮合队列
     * 2、循环处理单个订单 单个订单失败进行单个订单撤销
     *
     * @param tradeOrderCmds
     */
    public void processOrder(List<TradeOrderCmd> tradeOrderCmds) {
        if (CollectionUtil.isEmpty(tradeOrderCmds)) {
            return;
        }
        try {
            HintManager.getInstance().setMasterRouteOnly();
            TradeExecutor.startStopWatch("start-innodb_lock_wait_timeout-end");
            // 订单撮合 和 清算  批量事务提交
            requiredTransactionTemplate.execute(new TransactionCallbackWithoutResult() {
                @Override
                protected void doInTransactionWithoutResult(TransactionStatus status) {
                    long txStart = BizUtil.atomicLong();
                    BizUtil.EXCHANGE_WARN_LOG.warn("事务开始-----------start:txid-<{}>order-size-<{}>", txStart, tradeOrderCmds.size());
                    // 开启整个session lock wait time out 不用单个设置
                    bizMasterNamedParamTemplate.getJdbcTemplate().update("SET SESSION innodb_lock_wait_timeout = ?", new Object[]{1});
                    TradeExecutor.startStopWatch("for-processOrder-end");
                    // 存储线程版本号
                    Map<String, Long> threadVersionMap = new HashMap<>();
                    // 循环处理订单
                    for (TradeOrderCmd tradeOrderCmd : tradeOrderCmds) {
                        TradeOrder tradeOrder = tradeOrderCmd.getTradeOrder();
                        CoinTrader coinTrader = coinTraderFactory.getCoinTrader(tradeOrder.getPair());
                        if (!threadVersionMap.containsKey(tradeOrder.getPair())) {
                            threadVersionMap.put(tradeOrder.getPair(), coinTrader.getThreadVersion());
                        }
                        LOCAL_COIN_TRADER.get().put(tradeOrder.getPair(), coinTrader);
                        metricsUtil.timerMetric(SINGLE_PROCESS_ORDER_TPS_RT, () -> {
                            try {
                                BizUtil.BIZ_WARN_LOG.warn("处理订单订单信息...:<{}>,<{}>,<{}>", txStart, JSON.toJSONString(tradeOrderCmd), BizUtil.getBizThreadName());
                                ApiResponse apiResponse = coinTrader.processOrder(tradeOrderCmd, ProcessOrderTypeEnum.getByCode(tradeOrderCmd.getAction()), threadVersionMap.get(tradeOrder.getPair()));
                                // 下单撤单后 推送盘口信息
                                if (apiResponse.successful()) {
                                    exchangeKafkaUtil.sendPlateQueue(coinTrader);
                                }
                            } catch (Exception e) {
                                log.error("processOrder处理撮合订单失败,丢弃数据:<{}><{}><{}>"
                                        , JSON.toJSONString(tradeOrder),
                                        tradeOrderCmd.getAction(),
                                        JSON.toJSONString(TradeExecutor.getLocalExchangeResult()),
                                        e);
                                clearExchangeThreadLocal(true);
                            }
                        });
                    }
                    TradeExecutor.startStopWatch("batchProcessUserExAccount-end");
                    // 校验撮合接口扣除和实际账户余额是否正确
                    checkEqualToZeroAndWarnIfNecc();

                    // 批量提交 账户
                    Triple<List<UserCoinAccount>, List<UserCoinAccount>, List<UserCoinAccount>> insertUpdateDelAccounts = buildAccountList(LOCAL_LOCKED_ACCOUNTS.get());
                    exchangeBusiness.batchProcessUserExAccount(insertUpdateDelAccounts.getLeft(), WriterActionTypeEnum.ACTION_INSERT.getCode());
                    exchangeBusiness.batchProcessUserExAccount(insertUpdateDelAccounts.getMiddle(), WriterActionTypeEnum.ACTION_UPDATE.getCode());
                    exchangeBusiness.batchProcessUserExAccount(insertUpdateDelAccounts.getRight(), WriterActionTypeEnum.ACTION_DELETE.getCode());
                    TradeExecutor.startStopWatch("batchProcessTradeOrder-end");
                    // 验证数据是否更新到数据库
                    //checkUpdateAccountDbIfNecc();

                    // 批量提交 订单
                    Triple<List<TradeOrder>, List<TradeOrder>, List<TradeOrder>> insertUpdateDelOrders = buildOrdersList(LOCAL_TRADE_ORDER.get());
                    exchangeBusiness.batchProcessTradeOrder(insertUpdateDelOrders.getLeft(), WriterActionTypeEnum.ACTION_INSERT.getCode());
                    exchangeBusiness.batchProcessTradeOrder(insertUpdateDelOrders.getMiddle(), WriterActionTypeEnum.ACTION_UPDATE.getCode());
                    exchangeBusiness.batchProcessTradeOrder(insertUpdateDelOrders.getRight(), WriterActionTypeEnum.ACTION_DELETE.getCode());
                    BizUtil.EXCHANGE_WARN_LOG.warn("事务结束-----------end:start-<{}>end-<{}>kafka-size-<{}>acc-in-<{}>acc-up-<{}>acc-de-<{}>ord-in-<{}>ord-up-<{}>ord-de-<{}>",
                            txStart, BizUtil.atomicLong(), tradeOrderCmds.size(),
                            insertUpdateDelAccounts.getLeft().size(), insertUpdateDelAccounts.getMiddle().size(), insertUpdateDelAccounts.getRight().size(),
                            insertUpdateDelOrders.getLeft().size(), insertUpdateDelOrders.getMiddle().size(), insertUpdateDelOrders.getRight().size());
                    TradeExecutor.startStopWatch("requiredTransactionTemplate-end");
                }
            });
            TradeExecutor.startStopWatch("for-sendMsgForExchange-end");
            // 如果存在计划委托 下单和撤单 维护redis数据
            processStopLimitOrder(LOCAL_STOP_LIMIT_TRADE_ORDER.get());

            // 提交成功 批量发送消息
            for (ExchangeResult exchangeResult : LOCAL_EXCHANGE_RESULT.get()) {
                exchangeKafkaUtil.sendMsgForExchange(exchangeResult);
            }
            TradeExecutor.startStopWatch("end");
        } catch (Exception e) {
            clearExchangeThreadLocal(true);
            log.error("严重错误,批量提交撮合事务失败需要重新撮合:<{}>", JSON.toJSONString(tradeOrderCmds), e);
        } finally {
            HintManager.clear();
            //清空本地线程栈信息
            clearExchangeThreadLocal(false);
            TradeExecutor.stopStopWatchAndPrettyPrint();
        }
    }

    /**
     * 处理 计划委托   下单 撤单
     *
     * @param stopLimitOrderMap
     */
    private void processStopLimitOrder(Map<String, Pair<String, TradeOrder>> stopLimitOrderMap) {
        if (stopLimitOrderMap != null && stopLimitOrderMap.size() > 0) {
            for (Map.Entry<String, Pair<String, TradeOrder>> entry : stopLimitOrderMap.entrySet()) {
                if (WriterActionTypeEnum.ACTION_INSERT.getCode().equals(entry.getValue().getKey())) {
                    localCacheUtil.createLimitOrderCache(entry.getValue().getValue());
                } else if (WriterActionTypeEnum.ACTION_DELETE.getCode().equals(entry.getValue().getKey())) {
                    localCacheUtil.deleteStopLimitCache(entry.getValue().getValue());
                }
            }
        }
    }

    public static UserCoinAccount getTmpMemLockedAccount(String accountId) {
        UserCoinAccount userCoinAccount = LOCAL_TMP_ACCOUNTS.get().get(accountId);
        if (userCoinAccount == null) {
            userCoinAccount = getMemLockedAccount(accountId);
            if (userCoinAccount != null) {
                LOCAL_TMP_ACCOUNTS.get().put(accountId, userCoinAccount);
            }
        } else {
            BizUtil.ACCOUNT_WARN_LOG.warn("获取一级级账户缓存信息:<{}>", JSON.toJSONString(userCoinAccount));
        }
        return userCoinAccount;
    }

    public static BigDecimal getLocalCurrentReducecoinav() {
        return LOCAL_CURRENT_REDUCECOINAV.get();
    }

    public static void setLocalCurrentReducecoinav(BigDecimal coinAmount) {
        LOCAL_CURRENT_REDUCECOINAV.set(coinAmount);
    }

    private static UserCoinAccount getMemLockedAccount(String accountId) {
        // 获取二级缓存副本 业务层修改数据， 如果修改成功持久化 如果修改失败丢弃
        Pair<String, UserCoinAccount> accountPair = LOCAL_LOCKED_ACCOUNTS.get().get(accountId);
        if (accountPair != null) {
            UserCoinAccount userCoinAccount = new UserCoinAccount();
            BeanUtils.copyProperties(accountPair.getValue(), userCoinAccount);
            BizUtil.ACCOUNT_WARN_LOG.warn("获取二级账户缓存信息:<{}>", JSON.toJSONString(userCoinAccount));
            return userCoinAccount;
        }
        return null;
    }

    public static void updateTmpMemLockedAccount(UserCoinAccount userCoinAccount) {
        if (userCoinAccount != null) {
            LOCAL_TMP_ACCOUNTS.get().put(userCoinAccount.getId(), userCoinAccount);
        }
    }

    public static void updateMemLockedAccount(UserCoinAccount userCoinAccount, String action) {
        Pair<String, UserCoinAccount> coinAccountPair = LOCAL_LOCKED_ACCOUNTS.get().get(userCoinAccount.getId());
        BizUtil.ACCOUNT_WARN_LOG.warn("更新二级缓存信息:<{}><{}>", JSON.toJSONString(userCoinAccount),
                JSON.toJSONString(coinAccountPair != null ? coinAccountPair : "{}"));
        LOCAL_LOCKED_ACCOUNTS.get().put(userCoinAccount.getId(), ImmutablePair.of(action, userCoinAccount));
        if (WriterActionTypeEnum.ACTION_INSERT.getCode().equalsIgnoreCase(action)) {
            LOCAL_INSERT_ACCOUNTS.get().add(userCoinAccount.getId());
        }
    }

    public static UserCoinAccount getLocalTmpBkkFreeAccounts(String accountId) {
        return LOCAL_TMP_BKK_FREE_ACCOUNTS.get().get(accountId);
    }

    public static void updateLocalTmpBkkFreeAccounts(UserCoinAccount userCoinAccount) {
        if (userCoinAccount != null) {
            LOCAL_TMP_BKK_FREE_ACCOUNTS.get().put(userCoinAccount.getId(), userCoinAccount);
        }
    }

    public static void updateMemTradeOrder(List<TradeOrder> tradeOrders, String action) {
        if (!CollectionUtils.isEmpty(tradeOrders)) {
            for (TradeOrder tradeOrder : tradeOrders) {
                LOCAL_TRADE_ORDER.get().put(tradeOrder.getId(), ImmutablePair.of(action, tradeOrder));
                if (WriterActionTypeEnum.ACTION_INSERT.getCode().equalsIgnoreCase(action)) {
                    LOCAL_INSERT_TRADE_ORDER.get().add(tradeOrder.getId());
                } else if (WriterActionTypeEnum.ACTION_DELETE.getCode().equalsIgnoreCase(action)) {
                    LOCAL_DELETE_TRADE_ORDER.get().add(tradeOrder.getId());
                }
            }
        }
    }

    public static void updateLocalDbLockedAccounts(UserCoinAccount userCoinAccount) {
        UserCoinAccount oldUserCoinAccount = LOCAL_DB_LOCKED_ACCOUNTS.get().get(userCoinAccount.getId());
        if (oldUserCoinAccount != null) {
            BizUtil.ACCOUNT_WARN_LOG.warn("获取数据库账户脏读取:<{}><{}>", JSON.toJSONString(userCoinAccount), JSON.toJSONString(oldUserCoinAccount));
        }
        UserCoinAccount userCoinAccountRp = new UserCoinAccount();
        BeanUtils.copyProperties(userCoinAccount, userCoinAccountRp);
        LOCAL_DB_LOCKED_ACCOUNTS.get().put(userCoinAccount.getId(), userCoinAccountRp);
    }

    private static Triple<List<UserCoinAccount>, List<UserCoinAccount>, List<UserCoinAccount>> buildAccountList(Map<String, Pair<String, UserCoinAccount>> accountActions) {
        List<UserCoinAccount> insertAccounts = new ArrayList<>();
        List<UserCoinAccount> updateAccounts = new ArrayList<>();
        List<UserCoinAccount> deleteAccounts = new ArrayList<>();
        accountActions.forEach((mainKey, entry) -> {
            if (WriterActionTypeEnum.ACTION_INSERT.getCode().equals(entry.getKey())) {
                insertAccounts.add(entry.getValue());
            } else if (WriterActionTypeEnum.ACTION_UPDATE.getCode().equals(entry.getKey())) {
                if (LOCAL_INSERT_ACCOUNTS.get().contains(mainKey)) {
                    insertAccounts.add(entry.getValue());
                } else {
                    updateAccounts.add(entry.getValue());
                }
            } else {
                if (!LOCAL_INSERT_ACCOUNTS.get().contains(mainKey)) {
                    deleteAccounts.add(entry.getValue());
                }
            }
        });
        return Triple.of(insertAccounts, updateAccounts, deleteAccounts);
    }

    public static void addExchangeResult(ExchangeResult exchangeResult) {
        LOCAL_EXCHANGE_RESULT.get().add(exchangeResult);
        // 处理二级缓存订单信息
        updateMemTradeOrder(exchangeResult.getBizOrderAdded(), WriterActionTypeEnum.ACTION_INSERT.getCode());
        updateMemTradeOrder(exchangeResult.getBizOrderUpdated(), WriterActionTypeEnum.ACTION_UPDATE.getCode());
        updateMemTradeOrder(exchangeResult.getBizOrderDeleted(), WriterActionTypeEnum.ACTION_DELETE.getCode());

        // 计划委托 下单或者删除 更新redis
        TradeOrder newFocusedOrder = exchangeResult.getNewFocusedOrder();
        if (newFocusedOrder != null && BizUtil.isStopLimitOrder(newFocusedOrder)) {
            if (BalanceChangeTypeEnum.CREATE_TRADE.getCode().equalsIgnoreCase(exchangeResult.getAction())
                    && BizUtil.isCreateStopLimitOrder(newFocusedOrder)) {
                LOCAL_STOP_LIMIT_TRADE_ORDER.get().put(newFocusedOrder.getId(), ImmutablePair.of(WriterActionTypeEnum.ACTION_INSERT.getCode(), newFocusedOrder));
            } else if (BalanceChangeTypeEnum.CANCEL_TRADE.getCode().equalsIgnoreCase(exchangeResult.getAction())) {
                LOCAL_STOP_LIMIT_TRADE_ORDER.get().put(newFocusedOrder.getId(), ImmutablePair.of(WriterActionTypeEnum.ACTION_DELETE.getCode(), newFocusedOrder));
            }
        }
    }

    private static Triple<List<TradeOrder>, List<TradeOrder>, List<TradeOrder>> buildOrdersList(Map<String, Pair<String, TradeOrder>> orderActions) {
        List<TradeOrder> insertOrders = new ArrayList<>();
        List<TradeOrder> updateOrders = new ArrayList<>();
        List<TradeOrder> delOrders = new ArrayList<>();
        orderActions.forEach((id, entry) -> {
            if (WriterActionTypeEnum.ACTION_INSERT.getCode().equals(entry.getKey())) {
                insertOrders.add(entry.getValue());
            } else if (WriterActionTypeEnum.ACTION_UPDATE.getCode().equals(entry.getKey())) {
                if (LOCAL_INSERT_TRADE_ORDER.get().contains(id)) {
                    insertOrders.add(entry.getValue());
                } else {
                    updateOrders.add(entry.getValue());
                }
            } else {
                if (!LOCAL_INSERT_TRADE_ORDER.get().contains(id)) {
                    delOrders.add(entry.getValue());
                }
            }
        });
        return Triple.of(insertOrders, updateOrders, delOrders);
    }

    /**
     * 验证 本次会话 资金余额是否=所有撮合接口扣除余额
     */
    private void checkEqualToZeroAndWarnIfNecc() {
        try {
            Map<String, UserCoinAccount> oldUserCoinAccountMap = LOCAL_DB_LOCKED_ACCOUNTS.get();
            Map<String, Pair<String, UserCoinAccount>> newUserCoinAccountMap = LOCAL_LOCKED_ACCOUNTS.get();
            List<ExchangeResult> exchangeResults = LOCAL_EXCHANGE_RESULT.get();

            boolean isCheckAcountError = false;
            if (!oldUserCoinAccountMap.isEmpty() && newUserCoinAccountMap.isEmpty() && exchangeResults.isEmpty()) {
                List<BalanceChangeRecord> balanceChangeRecords = new ArrayList<>();
                for (ExchangeResult exchangeResult : exchangeResults) {
                    balanceChangeRecords.addAll(exchangeResult.getAllBalanceChangeRecord());
                }

                Map<String, AmtChangeInfo> allAmtChangeInfo = UserAccountsUtils.buildAmtChangeInfosFromBalanceRecords(
                        balanceChangeRecords);
                for (Map.Entry<String, AmtChangeInfo> entry : allAmtChangeInfo.entrySet()) {
                    String mainKey = entry.getKey();
                    AmtChangeInfo amtChangeInfo = entry.getValue();
                    UserCoinAccount newUserCoinAccount = newUserCoinAccountMap.get(mainKey).getValue();
                    UserCoinAccount oldUserCoinAccount = oldUserCoinAccountMap.get(mainKey);
                    if (oldUserCoinAccount != null) {
                        BigDecimal availableChange = newUserCoinAccount.getCoinAmount().subtract(oldUserCoinAccount.getCoinAmount());
                        BigDecimal frozenChange = newUserCoinAccount.getCoinFroze().subtract(oldUserCoinAccount.getCoinFroze());
                        if (amtChangeInfo.getAvailableChange().compareTo(availableChange) != 0
                                || amtChangeInfo.getFrozenChange().compareTo(frozenChange) != 0) {
                            isCheckAcountError = true;
                            break;
                        }
                    }
                }
            }
            boolean isAccountNumError = (oldUserCoinAccountMap.size() != newUserCoinAccountMap.size());
            if (isCheckAcountError) {
                log.error("会话检查：日常打印：账号误差：<{}>,是否对账失败<{}>,<{}>,<{}>,<{}>",
                        isAccountNumError,
                        isCheckAcountError,
                        JSON.toJSONString(oldUserCoinAccountMap),
                        JSON.toJSONString(newUserCoinAccountMap),
                        JSON.toJSONString(exchangeResults));
            }
        } catch (Exception e) {
            log.error("checkEqualToZeroAndWarnIfNecc会话检查异常:", e);
        }
    }

    /**
     * 如果刷新内存队列 如果已经存在已经处理的用户订单 需要进行一次补偿操作
     *
     * @param isInitOrderList 是否从新加载内存队列
     */
    public void clearExchangeThreadLocal(boolean isInitOrderList) {
        if (isInitOrderList) {
            log.error("clearExchangeThreadLocal撮合失败清除队列，重新加载内存订单:");
            try {
                for (ExchangeResult exchangeResult : LOCAL_EXCHANGE_RESULT.get()) {
                    exchangeKafkaUtil.sendExchangeSystemFailRetry(exchangeResult);
                }
            } catch (Exception e) {
                log.error("清除队列补偿错误:", e);
            }
            LOCAL_COIN_TRADER.get().values().forEach(coinTrader -> coinTrader.getReady().set(false));
        }
        LOCAL_COIN_TRADER.get().clear();
        LOCAL_TMP_ACCOUNTS.get().clear();
        LOCAL_LOCKED_ACCOUNTS.get().clear();
        LOCAL_INSERT_ACCOUNTS.get().clear();
        LOCAL_TRADE_ORDER.get().clear();
        LOCAL_EXCHANGE_RESULT.get().clear();
        LOCAL_DELETE_TRADE_ORDER.get().clear();
        LOCAL_CURRENT_REDUCECOINAV.set(BigDecimal.ZERO);
        LOCAL_INSERT_TRADE_ORDER.get().clear();
        LOCAL_STOP_LIMIT_TRADE_ORDER.get().clear();
        LOCAL_DB_LOCKED_ACCOUNTS.get().clear();
        LOCAL_TMP_BKK_FREE_ACCOUNTS.get().clear();
    }

    public static void clearSingleExchangeThreadLocal() {
        LOCAL_TMP_ACCOUNTS.get().clear();
        LOCAL_CURRENT_REDUCECOINAV.set(BigDecimal.ZERO);
        LOCAL_TMP_BKK_FREE_ACCOUNTS.get().clear();
    }

    public static List<ExchangeResult> getLocalExchangeResult() {
        return LOCAL_EXCHANGE_RESULT.get();
    }

    public static void startStopWatch(String taskName) {
        StopWatch stopWatch = LOCAL_STOP_WATCH.get();
        if (stopWatch == null) {
            stopWatch = new StopWatch();
            LOCAL_STOP_WATCH.set(stopWatch);
        }
        if (stopWatch.isRunning()) {
            stopWatch.stop();
        }
        stopWatch.start(taskName);
    }

    public static void stopStopWatchAndPrettyPrint() {
        StopWatch stopWatch = LOCAL_STOP_WATCH.get();
        if (stopWatch != null) {
            if (stopWatch.isRunning()) {
                stopWatch.stop();
            }
            if (stopWatch.getTotalTimeSeconds() > STOP_WATCH_AND_PRETTY_PRINT_TIME_S) {
                log.error("StopWatch批量撮合时间超时：<{}>", stopWatch.prettyPrint());
            }
        }
        LOCAL_STOP_WATCH.remove();
    }
}
