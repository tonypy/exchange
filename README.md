# Exchange

## 1. Project Overview

Exchange is a Java backend for a digital asset exchange. It handles order submission, cancellation, and matching, and maintains order-book depth for each trading pair. Kafka delivers trade, cancellation, and balance-change events to downstream services for order updates and data synchronization.

The six-module Maven project includes three Spring Boot applications for API access, matching, and synchronization. The stack includes Java 8, Spring Boot, Spring Cloud OpenFeign, Nacos, Kafka, Redis, Maven, and Elasticsearch.

## 2. Project Modules

| Module | Responsibilities |
|---|---|
| `exchange-api` | Defines shared interfaces and contracts for exchange operations, matching results, batch cancellation, order-book depth, and synchronization. |
| `exchange-impl` | Implements per-pair matching, order command processing, order-book maintenance, and Kafka event handling. |
| `exchange-db` | Provides database entities and mappers for orders, trades, user exchange accounts, balance history, and fee statistics. |
| `exchange-rest` | Hosts the HTTP-facing exchange APIs and web configuration. |
| `exchange-engine` | Initializes the matching engine and processes order and cancellation commands. |
| `exchange-sync` | Consumes Kafka events and synchronizes trade records with the database and Elasticsearch. |

## 3. Running the Project

### Environment and Startup

Install Java 8 and Maven, configure access to internal `com.weibi` Maven dependencies, and set up Nacos, Kafka, Redis, the trade database, and Elasticsearch. Run `mvn clean package` from the project root. Start each application with its corresponding Nacos configuration.

| Service | Spring Boot entry point |
|---|---|
| `exchange-engine` | `ExchangeEngineApplication` |
| `exchange-sync` | `ExchangeSyncApplication` |
| `exchange-rest` | `ExchangeRestApplication` |

### End-to-End Order Test

Use a configured trading pair and funded test accounts. Refer to `ExchangeService` for request parameters.

1. Submit a limit order using `POST /exchange/createOrderForUser`.
2. Check depth and open orders using `GET /exchange/getQuotationOrdersDepth` and `GET /exchange/getTradeOrderUnfinishedList`.
3. Submit a compatible opposite-side order. Check the Kafka trade event and synchronized trade record.
4. Cancel a separate open order using `POST /exchange/cancelOrderForUser`. Verify that the order disappears from the open-order list and a cancellation event is published.

Startup and API calls have not been verified in a running environment.

## 4. Deployment Setup

### Kafka Topics

The deployment notes use the ZooKeeper-based Kafka CLI. Set `ZOOKEEPER` to the target environment address.

```bash
./kafka-topics.sh --create --zookeeper "$ZOOKEEPER" --replication-factor 1 --partitions 54 --topic exchange-order-comm-v1
./kafka-topics.sh --create --zookeeper "$ZOOKEEPER" --replication-factor 1 --partitions 12 --topic exchange-order-hot-v1
./kafka-topics.sh --create --zookeeper "$ZOOKEEPER" --replication-factor 1 --partitions 1 --topic exchange-public-fee-v1
./kafka-topics.sh --create --zookeeper "$ZOOKEEPER" --replication-factor 1 --partitions 6 --topic exchange-plate-queue-v1

for topic in deal_made order_filled order_canceled order_failed BALANCE_RES_FROM_MATCH_ENGINE order_added_to_queue; do
  ./kafka-topics.sh --zookeeper "$ZOOKEEPER" --alter --topic "$topic" --partitions 3
done
```

The source gives conflicting partition counts for `exchange-plate-queue-v1` (six at creation and three in a later alteration). Confirm the intended count before creating it. Kafka cannot decrease an existing topic's partition count.

### Nacos and Feign

Add Nacos configuration for all three applications and map their names in `feign-servers.yaml`.

```yaml
exchange-engine: exchange-engine
exchange-rest: exchange-rest
exchange-sync: exchange-sync
```

Add `listener.listener_user_balance_change` to the `webapp` configuration. The deployment notes record these settings in `environment-properties.yaml`.

```yaml
newExchangePair: LTC_USDT
newOrOldExchangeRoute: false
```

The first setting selects the rollout trading pair. The second controls legacy matching routing.

### Related Services

The deployment notes reference `feature/stage` and `feature/jsg_exchange_20220208`.

| Component | Release action |
|---|---|
| `tradeparent` | Merge into staging with completed-order persistence removed, publish the package, and build and deploy `webapp`. |
| `account-service` | Remove Kafka and Elasticsearch consumer logic, merge into staging, publish the package, and restart `webapp`. |
| `common-tech-components`, `config-service` | Merge into `master` and publish the packages. |
| `exchange`, `bkex_api`, `bkex_admin` | Include the exchange services, API component, and administration application in the release. |
| `webapp` | Merge into staging and route calls to the new matching engine. |

## 5. Kafka Events and Consumer Groups

### Topics and Downstream Actions

| Constant | Topic | Purpose |
|---|---|---|
| `TOPIC_EXCHANGE_ORDER_CREATED` | `exchange-order-created` | Queues an order for matching. |
| `TOPIC_EXCHANGE_ORDER_CANCELED` | `exchange-order-canceled` | Queues a cancellation request. |
| `TOPIC_ORDER_FILLED` | `order_filled` | Reports a completed fill. The notes list Elasticsearch indexing by `account-service`, database persistence by `tradeparent`, and JPush notifications from `webapp`. |
| `TOPIC_ORDER_CANCELED` | `order_canceled` | Reports a completed cancellation. The notes list the same indexing, persistence, and notification destinations. |
| `TOPIC_ORDER_FAILED` | `order_failed` | Reports an order failure, including insufficient balance when a conditional order is triggered. |
| `TOPIC_ORDER_ADDED_TO_QUEUE` | `order_added_to_queue` | Reports that an order entered the matching queue. The notes associate it with Elasticsearch indexing by `account-service`. |
| `TOPIC_BALANCE_RES_INIT_FROM_MATCH_ENGINE` | `BALANCE_RES_FROM_MATCH_ENGINE` | Reports balance changes. The notes list socket updates and DingTalk alerts from `webapp`, plus database and Elasticsearch records from `account-service`. |

The deployment checklist removes some downstream consumers. Confirm persistence and indexing ownership for the branch being deployed.

### Consumer Configuration

| Group | Purpose | Topics | Batch threads | Auto-start |
|---|---|---|---:|---|
| `exchangeOrderDefaultGroup` | Matching queue | `exchange-order` | 32 | Disabled |
| `activeStopLimit` | Conditional order activation | `deal_made` | 3 | Disabled |
| `syncToDatacenterV1` | Elasticsearch indexing | `order_filled`, `order_canceled`, `order_failed`, `BALANCE_RES_FROM_MATCH_ENGINE`, `SUCC_BALANCE_HIST_WRITTEN`, `deal_made`, `general_deal` | 6 | Unspecified |
| `transMatchEngine` | Balance persistence | `BALANCE_RES_FROM_MATCH_ENGINE`, `BALANCE_DELEGA_MANAGE_HISORY` | 3 | Unspecified |
| `saveTradeDeal` | Trade persistence | `deal_made` | 3 | Unspecified |
| `saveFinishedOrder` | Finalized order persistence | `order_filled`, `order_canceled`, `order_failed` | 3 | Unspecified |

## 6. Maintenance and Diagnostics

### Database Queries

```sql
SELECT * FROM bkex_exchange.tbl_user_ex_account WHERE id = '<wallet_account_id>';
SELECT * FROM tbl_trade_order_unfinished WHERE id = '<open_order_id>';
```

| Configuration table | Purpose |
|---|---|
| `bkex_exchange.tbl_trade_pair_frequency` | High-activity trading pair configuration. |
| `bkex_exchange.tbl_machine_static_config` | Spot trading bot accounts. |
| `bkex_machine.tbl_etp_static_config` | Spot ETP trading bot accounts. |

### Kafka Diagnostics

Set `ZOOKEEPER` and `BOOTSTRAP_SERVER` for the target environment. Pass an exact deployed topic or group name as `TOPIC` or `GROUP`.

```bash
unset JMX_PORT
./kafka-topics.sh --zookeeper "$ZOOKEEPER" --list
./kafka-topics.sh --zookeeper "$ZOOKEEPER" --describe --topic "$TOPIC"
./kafka-consumer-groups.sh --bootstrap-server "$BOOTSTRAP_SERVER" --list
./kafka-consumer-groups.sh --bootstrap-server "$BOOTSTRAP_SERVER" --group "$GROUP" --describe
```

| Diagnostic area | Identifiers |
|---|---|
| Kafka topics | `exchange-order`, `order_added_to_queue`, `exchange-order-comm-v1`, `exchange-order-hot-v1`, `deal_made`, `general_deal`, `order_filled`, `order_canceled`, `order_failed`, `BALANCE_DELEGA_MANAGE_HISORY`, `SUCC_BALANCE_HIST_WRITTEN`, `BALANCE_RES_FROM_MATCH_ENGINE`, `exchange-public-fee-v1`, `exchange-plate-queue-v1` |
| Matching queues | `exchangeOrderDefaultGroupV1`, `exchangeOrderHotDefaultGroupV1` |
| Elasticsearch indexing | `syncToDatacenterV1` |
| Conditional order activation | `activeStopLimitV1` |
| Database persistence | `transMatchEngineV1`, `saveTradeDealV1`, `saveFinishedOrderV1` |
| Fee processing | `dealMiningV1` for persistence and `factoryPublicFeeV1` for calculation records |
| Order-book updates | `exchangePlateQueueV1_*` |
| Legacy matching | `activeStopLimit`, `transMatchEngine`, `saveFinishedOrder`, `saveTradeDeal`, `syncToDatacenter` |
| WebSocket trade updates | `consumerTradeOrderDealhera_1` |
| WebSocket order updates | `consumerTradeOrderAddToQuene+xx`, `consumerTradeOrderAddToQuenehera_2` |
| WebSocket wallet updates | `consumerNotifyWalletUpdate+xx` |

For patterns containing `*` or ending in `+xx`, list deployed groups first and use the exact name.

### Redis Keys for Conditional Orders

| Key pattern | Purpose |
|---|---|
| `trade:stop_limit:<pair>:lte` | Stop-loss queue with trigger price less than or equal to market price. |
| `trade:stop_limit:<pair>:gte` | Take-profit queue with trigger price greater than or equal to market price. |
| `trade:stop_limit:<userId>:<pair>` | Conditional order count for a user and trading pair. |

## 7. Downstream HTTP and WebSocket Interfaces

### HTTP Endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/q/trade/quotation` | Full market quotation snapshot. |
| GET | `/asset/v5/coinLogoMa` | Cryptocurrency icons. |
| GET | `/p/config/pair/symbols` | Configuration for all trading pairs. |
| GET | `/coinCurrency/anchoredCoins` | Exchange-rate data. |
| GET | `/q/trade/depth` | Order-book depth. |
| GET | `/p/config/pair/tip` | Current trading pair notice. |
| GET | `/p/config/time/info` | Current trading pair listing countdown. |
| GET | `/u/trade/orders/list` | Current user's open orders. |
| GET | `/u/user/getFeeProfile` | BKK fee discount setting. |
| GET | `/u/user/queryOrderConfirm` | Order confirmation setting. |
| POST | `/u/user/setOrderConfirm` | Updates the order confirmation setting. |
| GET | `/p/config/etp/relation` | ETP information for the current pair. |
| GET | `/pair/tradeConfig` | Trading fee information. |
| GET | `/q/trade/kline` | Candlestick data. |
| POST | `/u/cancel_trade_order_newV2` | Cancels one open order. |
| POST | `/u/cancel_trade_orders` | Cancels multiple open orders. |
| POST | `/u/create_trade_order_newV2` | Submits an order. |

### WebSocket Events

| Action | Event | Purpose |
|---|---|---|
| Emit | `subQuotationPair` | Called after connection. Its exact behavior is unconfirmed in the source notes. |
| Emit | `moreSubKlineByRange` | Subscribes to candlesticks for a trading pair and time range. |
| Listen | `pairKlineByRange` | Receives candlestick data. |
| Listen | `qPairsKLine` | Receives the latest candle for chart updates. |
| Emit | `quotationDealConnect` | Subscribes to trade updates. |
| Listen | `quotationAllDeal` | Receives a full trade snapshot. |
| Listen | `quotationListDeal` | Receives incremental trade updates. |
| Emit | `subOrderDepth` | Subscribes to order-book depth. |
| Listen | `quotationOrderDepth` | Receives a full order-book snapshot. |
| Listen | `qPriceQuantity` | Receives incremental order-book updates. |
| Listen | `qPairsUpdateStats` | Receives incremental market statistics. |
| Emit | `userConnect` | Subscribes to user-specific updates when switching trading pairs. |
| Listen | `userTradeInfo` | Triggers a refresh of the current user's open orders for the selected market. |

Environment-specific addresses and record IDs are represented by placeholders. Service, endpoint, topic, and consumer group identifiers retain their original spelling.
