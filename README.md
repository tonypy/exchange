Exchange
1. Project Overview
Exchange is a Java backend for a digital asset exchange. It handles order submission, cancellation, and matching, and maintains order-book depth for each trading pair. The matching engine publishes trade, cancellation, and balance-change events through Kafka so downstream services can update order records and synchronize transaction data.

The codebase is a six-module Maven project with three independently runnable Spring Boot applications for REST API access, order matching, and data synchronization.

Technology stack — Java 8, Spring Boot, Spring Cloud OpenFeign, Nacos, Kafka, Redis, Maven, and Elasticsearch.
2. Project Modules
Module	Responsibilities
exchange-api	Defines shared service interfaces and data contracts for exchange operations, matching results, batch cancellations, order-book depth, and synchronization.
exchange-impl	Implements core business logic, including per-pair order matching, order command processing, order-book maintenance, and Kafka event handling.
exchange-db	Provides database entities and mappers for open and completed orders, trade records, user exchange accounts, balance history, and fee statistics.
exchange-rest	Provides the HTTP-facing application entry point and web configuration for exchange APIs.
exchange-engine	Initializes the matching engine at startup and uses the logic in exchange-impl to process order and cancellation commands.
exchange-sync	Uses shared Kafka listeners to consume trade events and synchronize records with the database and Elasticsearch.


3. Running the Project
3.1 Set Up the Environment
Install Java 8 and Maven, and configure access to the internal com.weibi Maven dependencies. Set up Nacos for configuration and service discovery, then configure Kafka, Redis, the trade database, and Elasticsearch.
3.2 Build and Start the Services
Run the following command from the Maven project root.
mvn clean package
Start each Spring Boot application with its corresponding Nacos configuration.
Service	Spring Boot entry point
exchange-engine	ExchangeEngineApplication
exchange-sync	ExchangeSyncApplication
exchange-rest	ExchangeRestApplication


3.3 Test an Order End to End
Use a configured trading pair and funded test accounts. The ExchangeService interface defines the request parameters.
1. Submit a limit order using POST /exchange/createOrderForUser.
2. Check order-book depth and open orders using GET /exchange/getQuotationOrdersDepth and GET /exchange/getTradeOrderUnfinishedList.
3. Submit a compatible order on the opposite side. Check the Kafka trade event and the synchronized trade record.
4. Cancel a separate open order using POST /exchange/cancelOrderForUser. Verify that it disappears from the open-order list and that a cancellation event is published.
These steps come from the repository README and service interfaces. Startup and API calls have not been verified in a running environment.
4. Deployment Setup
4.1 Configure Kafka Topics
The original deployment notes use the ZooKeeper-based Kafka CLI. Set ZOOKEEPER to the address for the target environment.
./kafka-topics.sh --create --zookeeper "$ZOOKEEPER" --replication-factor 1 --partitions 54 --topic exchange-order-comm-v1
./kafka-topics.sh --create --zookeeper "$ZOOKEEPER" --replication-factor 1 --partitions 12 --topic exchange-order-hot-v1
./kafka-topics.sh --create --zookeeper "$ZOOKEEPER" --replication-factor 1 --partitions 1 --topic exchange-public-fee-v1
./kafka-topics.sh --create --zookeeper "$ZOOKEEPER" --replication-factor 1 --partitions 6 --topic exchange-plate-queue-v1

./kafka-topics.sh --zookeeper "$ZOOKEEPER" --alter --topic deal_made --partitions 3
./kafka-topics.sh --zookeeper "$ZOOKEEPER" --alter --topic order_filled --partitions 3
./kafka-topics.sh --zookeeper "$ZOOKEEPER" --alter --topic order_canceled --partitions 3
./kafka-topics.sh --zookeeper "$ZOOKEEPER" --alter --topic order_failed --partitions 3
./kafka-topics.sh --zookeeper "$ZOOKEEPER" --alter --topic BALANCE_RES_FROM_MATCH_ENGINE --partitions 3
./kafka-topics.sh --zookeeper "$ZOOKEEPER" --alter --topic order_added_to_queue --partitions 3
The source also lists an alteration to three partitions for exchange-plate-queue-v1, although its creation command specifies six. Kafka cannot reduce an existing topic’s partition count. Confirm the intended count before creating the topic.
4.2 Configure Nacos and Feign
Add Nacos configuration for exchange-engine, exchange-rest, and exchange-sync. Add the following service mappings to feign-servers.yaml.
exchange-engine: exchange-engine
exchange-rest: exchange-rest
exchange-sync: exchange-sync
Add listener.listener_user_balance_change to the webapp configuration. The deployment notes also record these values in environment-properties.yaml.
newExchangePair: LTC_USDT
newOrOldExchangeRoute: false
newExchangePair identifies the trading pair being rolled out. newOrOldExchangeRoute controls legacy matching routing.
4.3 Build and Release Related Services
The deployment notes reference the feature/stage and feature/jsg_exchange_20220208 branches.
Component	Deployment action
tradeparent	Merge into staging with completed-order persistence removed, publish the package, and build and deploy webapp.
account-service	Remove Kafka and Elasticsearch consumer logic, merge into staging, publish the package, and restart webapp.
common-tech-components	Merge into master and publish the package.
config-service	Merge into master and publish the package.
exchange	Include the exchange services in the release.
bkex_api	Include the API component in the release.
webapp	Merge into staging and route calls to the new matching engine.
bkex_admin	Include the administration application in the release.


5. Kafka Events and Consumer Groups
5.1 Topics and Downstream Actions
Constant	Topic	Purpose and downstream actions
TOPIC_EXCHANGE_ORDER_CREATED	exchange-order-created	Queues a new order for matching.
TOPIC_EXCHANGE_ORDER_CANCELED	exchange-order-canceled	Queues an order cancellation request.
TOPIC_ORDER_FILLED	order_filled	Reports a completed fill. The event is indexed by account-service, persisted through tradeparent, and sent to users through JPush in webapp.
TOPIC_ORDER_CANCELED	order_canceled	Reports a completed cancellation. The event is indexed by account-service, persisted through tradeparent, and sent to users through JPush in webapp.
TOPIC_ORDER_FAILED	order_failed	Reports an order failure, including insufficient balance when a conditional order is triggered.
TOPIC_ORDER_ADDED_TO_QUEUE	order_added_to_queue	Reports that an order entered the matching queue. The notes also associate this event with Elasticsearch indexing by account-service.
TOPIC_BALANCE_RES_INIT_FROM_MATCH_ENGINE	BALANCE_RES_FROM_MATCH_ENGINE	Reports a trading balance change. webapp sends a socket update and a DingTalk alert, and account-service records the change in the database and Elasticsearch.


The event descriptions reflect the original notes. The deployment checklist also calls for removing some downstream consumers, so confirm which service handles persistence and indexing for the branch being deployed.
5.2 Consumer Configuration
Consumer group	Purpose	Topics	Batch threads	Automatic startup
exchangeOrderDefaultGroup	Processes orders for matching.	exchange-order	32	Disabled
activeStopLimit	Activates conditional orders.	deal_made	3	Disabled
syncToDatacenterV1	Indexes orders, trades, and balance records in Elasticsearch.	order_filled, order_canceled, order_failed, BALANCE_RES_FROM_MATCH_ENGINE, SUCC_BALANCE_HIST_WRITTEN, deal_made, general_deal	6	Unspecified
transMatchEngine	Persists balance records.	BALANCE_RES_FROM_MATCH_ENGINE, BALANCE_DELEGA_MANAGE_HISORY	3	Unspecified
saveTradeDeal	Persists trade records.	deal_made	3	Unspecified
saveFinishedOrder	Persists finalized order records.	order_filled, order_canceled, order_failed	3	Unspecified


6. Maintenance and Diagnostics
6.1 Database Queries
Replace the example identifiers with values from the target environment.
SELECT *
FROM bkex_exchange.tbl_user_ex_account
WHERE id = '<wallet_account_id>';

SELECT *
FROM tbl_trade_order_unfinished
WHERE id = '<open_order_id>';
Configuration table	Purpose
bkex_exchange.tbl_trade_pair_frequency	Configuration for high-activity trading pairs.
bkex_exchange.tbl_machine_static_config	Account configuration for spot trading bots.
bkex_machine.tbl_etp_static_config	Account configuration for spot ETP trading bots.


6.2 Kafka Topic and Consumer Diagnostics
Set ZOOKEEPER and BOOTSTRAP_SERVER for the target environment. Replace TOPIC and GROUP with the appropriate identifiers.
unset JMX_PORT

./kafka-topics.sh --zookeeper "$ZOOKEEPER" --list
./kafka-topics.sh --zookeeper "$ZOOKEEPER" --describe --topic "$TOPIC"

./kafka-consumer-groups.sh --bootstrap-server "$BOOTSTRAP_SERVER" --list
./kafka-consumer-groups.sh --bootstrap-server "$BOOTSTRAP_SERVER" --group "$GROUP" --describe
Diagnostic area	Topics or consumer groups
Kafka topics	exchange-order, order_added_to_queue, exchange-order-comm-v1, exchange-order-hot-v1, deal_made, general_deal, order_filled, order_canceled, order_failed, BALANCE_DELEGA_MANAGE_HISORY, SUCC_BALANCE_HIST_WRITTEN, BALANCE_RES_FROM_MATCH_ENGINE, exchange-public-fee-v1, exchange-plate-queue-v1
Matching queues	exchangeOrderDefaultGroupV1, exchangeOrderHotDefaultGroupV1
Elasticsearch indexing	syncToDatacenterV1
Conditional order activation	activeStopLimitV1
Database persistence	transMatchEngineV1, saveTradeDealV1, saveFinishedOrderV1
Fee processing	dealMiningV1 for fee persistence and factoryPublicFeeV1 for fee calculation records
Order-book updates	exchangePlateQueueV1_*
Legacy matching	activeStopLimit, transMatchEngine, saveFinishedOrder, saveTradeDeal, syncToDatacenter
WebSocket trade updates	consumerTradeOrderDealhera_1
WebSocket order submission and cancellation updates	consumerTradeOrderAddToQuene+xx, consumerTradeOrderAddToQuenehera_2
WebSocket wallet updates	consumerNotifyWalletUpdate+xx


For group patterns such as exchangePlateQueueV1_* and names ending in +xx, list the deployed groups and use the exact group name in the --describe command.
6.3 Redis Keys for Conditional Orders
Purpose	Key pattern
Stop-loss queue, where the trigger price is less than or equal to the market price	trade:stop_limit:<pair>:lte
Take-profit queue, where the trigger price is greater than or equal to the market price	trade:stop_limit:<pair>:gte
Number of conditional orders for a user and trading pair	trade:stop_limit:<userId>:<pair>


7. Downstream HTTP and WebSocket Interfaces
These interfaces document how downstream applications retrieve market data, manage orders, and receive real-time updates.
7.1 HTTP Endpoints
Method	Endpoint	Purpose
GET	/q/trade/quotation	Retrieves a full market quotation snapshot.
GET	/asset/v5/coinLogoMa	Retrieves cryptocurrency icons.
GET	/p/config/pair/symbols	Retrieves configuration for all trading pairs.
GET	/coinCurrency/anchoredCoins	Retrieves exchange-rate data.
GET	/q/trade/depth	Retrieves order-book depth.
GET	/p/config/pair/tip	Retrieves the notice for the current trading pair.
GET	/p/config/time/info	Retrieves the listing countdown for the current trading pair.
GET	/u/trade/orders/list	Retrieves the current user’s open orders.
GET	/u/user/getFeeProfile	Checks whether the current user has enabled the BKK fee discount.
GET	/u/user/queryOrderConfirm	Retrieves the user’s order confirmation setting.
POST	/u/user/setOrderConfirm	Updates the user’s order confirmation setting.
GET	/p/config/etp/relation	Retrieves ETP information for the current trading pair.
GET	/pair/tradeConfig	Retrieves trading fee information for the current trading pair.
GET	/q/trade/kline	Retrieves candlestick data for the current trading pair.
POST	/u/cancel_trade_order_newV2	Cancels a single open order.
POST	/u/cancel_trade_orders	Cancels multiple open orders in one request.
POST	/u/create_trade_order_newV2	Submits a new order.


7.2 WebSocket Events
Client action	Event	Purpose
Emit	subQuotationPair	Called after the socket connects. The source notes leave its exact behavior unconfirmed.
Emit	moreSubKlineByRange	Subscribes to candlestick data for a trading pair and time range.
Listen	pairKlineByRange	Receives candlestick data returned by the server.
Listen	qPairsKLine	Receives the latest candle so the client can update the chart.
Emit	quotationDealConnect	Subscribes to trade updates.
Listen	quotationAllDeal	Receives a full trade snapshot.
Listen	quotationListDeal	Receives incremental trade updates.
Emit	subOrderDepth	Subscribes to order-book depth updates.
Listen	quotationOrderDepth	Receives a full order-book snapshot.
Listen	qPriceQuantity	Receives incremental order-book updates.
Listen	qPairsUpdateStats	Receives incremental market statistics.
Emit	userConnect	Subscribes to user-specific updates when the user switches trading pairs.
Listen	userTradeInfo	Prompts the client to refresh open orders for the current user and selected market.


Environment-specific addresses and record IDs have been replaced with placeholders. Service names, endpoint paths, topic names, and consumer group identifiers retain their original spelling.
