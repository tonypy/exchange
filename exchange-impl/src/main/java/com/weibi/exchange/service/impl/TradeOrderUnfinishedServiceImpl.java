package com.weibi.exchange.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.convert.Convert;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.weibi.config.enums.TradeDirectionEnum;
import com.weibi.config.model.trade.TradeOrder;
import com.weibi.core.commons.db.extension.service.impl.BaseServiceImpl;
import com.weibi.core.commons.logging.ILogging;
import com.weibi.core.commons.model.mybatis.BasePage;
import com.weibi.core.commons.util.NumberUtil;
import com.weibi.exchange.entity.TradeOrderUnfinishedEntity;
import com.weibi.exchange.mapper.TradeOrderUnfinishedMapper;
import com.weibi.exchange.model.OrdersDepthDTO;
import com.weibi.exchange.model.TradeOrderUnfinished;
import com.weibi.exchange.service.TradeOrderUnfinishedService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 挂单表 服务实现类
 *
 * @author jsong
 * @since 2022-02-07
 */
@Service
@Primary
public class TradeOrderUnfinishedServiceImpl extends BaseServiceImpl<TradeOrderUnfinishedEntity> implements TradeOrderUnfinishedService, ILogging {

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
    private static final String SELECT_PAIR_BY_USERID = "select pair from bkex_exchange.tbl_trade_order_unfinished where user_id =? group by pair";

    @Autowired
    private TradeOrderUnfinishedMapper tradeOrderUnfinishedMapper;
    @Resource
        private NamedParameterJdbcTemplate bizMasterNamedParamTemplate;

    @Override
    public Integer countExUnfinishedOrderByIdAndPair(String userId, String pair, String direction) {
        return tradeOrderUnfinishedMapper.selectCount(new QueryWrapper<TradeOrderUnfinishedEntity>()
                .eq("user_id", userId)
                .eq("pair", pair)
                .eq("direction", direction));
    }

    @Override
    public List<TradeOrder> findAllTradingOrderBySymbol(String symbol) {
        Map<String, Object> param = new HashMap<>();
        param.put("pair", symbol);
        List<TradeOrder> tradeOrders = bizMasterNamedParamTemplate.query(SELECT_ORDERS_BY_SYMBOL,
                param,
                new BeanPropertyRowMapper<TradeOrder>(TradeOrder.class));
        return tradeOrders;
    }

    @Override
    public TradeOrder findOrderById(String id) {
        List<TradeOrder> resultList = bizMasterNamedParamTemplate.getJdbcTemplate().query(SELECT_ORDER_BY_ID,
                new Object[]{id}, new BeanPropertyRowMapper(TradeOrder.class));
        return resultList != null && resultList.size() > 0 ? resultList.get(0) : null;
    }

    @Override
    public void batchUpdateTradeOrder(List<TradeOrder> orders) {
        if (orders == null || orders.size() == 0) {
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

    @Override
    public BasePage<TradeOrderUnfinished> getTradeOrderUnfinishedList(
            String id, String userId, String pair, String direction, String src, String orderType,
            Integer status, Integer page, Integer pageSize) {
        return tradeOrderUnfinishedMapper.selectPage(
                new BasePage<>(page, pageSize),
                new QueryWrapper<TradeOrderUnfinishedEntity>()
                        .eq(StringUtils.isNotEmpty(id), "id", id)
                        .eq(StringUtils.isNotEmpty(userId), "user_id", userId)
                        .eq(StringUtils.isNotEmpty(pair), "pair", pair)
                        .eq(StringUtils.isNotEmpty(direction), "direction", direction)
                        .eq(StringUtils.isNotEmpty(src), "src", src)
                        .eq(StringUtils.isNotEmpty(orderType), "order_type", orderType)
                        .eq(status != null, "status", status)
        ).convert(TradeOrderUnfinished.class);
    }

    @Override
    public List<TradeOrderUnfinished> getTradeOrderUnfinishedByIdsAndUserId(List<String> ids, String userId) {
        List<TradeOrderUnfinishedEntity> tradeOrderUnfinishedEntities = tradeOrderUnfinishedMapper.selectList(new QueryWrapper<TradeOrderUnfinishedEntity>()
                .in(CollectionUtil.isEmpty(ids), "id", ids)
                .eq(StringUtils.isNotEmpty(userId), "user_id", userId));
        return Convert.toList(TradeOrderUnfinished.class, tradeOrderUnfinishedEntities);
    }

    @Override
    public List<TradeOrderUnfinished> getTradeOrderUnfinishedByIdsAndPair(List<String> ids, String pair) {
        List<TradeOrderUnfinishedEntity> tradeOrderUnfinishedEntities = tradeOrderUnfinishedMapper.selectList(new QueryWrapper<TradeOrderUnfinishedEntity>()
                .in(CollectionUtil.isEmpty(ids), "id", ids)
                .eq(StringUtils.isNotEmpty(pair), "pair", pair));
        return Convert.toList(TradeOrderUnfinished.class, tradeOrderUnfinishedEntities);
    }

    @Override
    public List<TradeOrderUnfinished> getTradeOrderUnfinishedByIdsAndUserIdAndPair(List<String> ids, String userId, String pair) {
        List<TradeOrderUnfinishedEntity> tradeOrderUnfinishedEntities = tradeOrderUnfinishedMapper.selectList(new QueryWrapper<TradeOrderUnfinishedEntity>()
                .in(CollectionUtil.isNotEmpty(ids), "id", ids)
                .eq(StringUtils.isNotEmpty(pair), "pair", pair)
                .eq(StringUtils.isNotEmpty(userId), "user_id", userId));
        return Convert.toList(TradeOrderUnfinished.class, tradeOrderUnfinishedEntities);
    }

    @Override
    public List<String> getPairForTradeOrderUnfinished(String userId) {
        return tradeOrderUnfinishedMapper.getPairByUserId(userId);
    }

    @Override
    public void batchInsertTradeOrder(List<TradeOrder> orders) {
        if (orders == null || orders.size() == 0) {
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

    @Override
    public void batchDeleteTradeOrder(List<TradeOrder> orders) {
        if (orders == null || orders.size() == 0) {
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

    @Override
    public List<OrdersDepthDTO> getOrdersDepth(String pair, boolean isBid, int maxSize) {
        StringBuilder querySqlSb = new StringBuilder();
        querySqlSb.append("select pair,direction,price,sum(total_amount - deal_amount) exchangeAmount,count(*) from bkex_exchange.tbl_trade_order_unfinished ");
        querySqlSb.append(" where pair = ? and direction = ? and status in (0,3) ");
        querySqlSb.append(" group by pair,direction,price ");
        querySqlSb.append(" order by pair,direction,price ").append(isBid ? "desc" : "asc");
        querySqlSb.append(" limit ").append(maxSize);

        String direction = isBid ? TradeDirectionEnum.BID.getCode() : TradeDirectionEnum.ASK.getCode();

        return bizMasterNamedParamTemplate.getJdbcTemplate().query(querySqlSb.toString(), new Object[]{pair, direction}, new BeanPropertyRowMapper<>(OrdersDepthDTO.class));
    }
}
