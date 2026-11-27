Project Description
Exchange is a Java backend for a digital asset exchange. It handles order submission, cancellation, and matching, and maintains order-book depth for trading pairs. The matching service publishes trade, cancellation, and balance-change events through Kafka so downstream services can update order records and synchronize transaction data.

The codebase is organized as a six-module Maven project, with separate Spring Boot applications for API serving, order matching, and data synchronization. The main technology stack includes Java 8, Spring Boot, Spring Cloud OpenFeign, Nacos, Kafka, Redis, and Maven.
Project Modules
Module	Responsibilities
exchange-api	Defines shared service interfaces and data contracts for exchange operations, matching results, batch cancellations, order-book depth, and synchronization. Other modules use these contracts to communicate.
exchange-impl	Contains the core business logic, including per-pair order matching, order command processing, order-book maintenance, and Kafka event handling. It also implements the services shared by the three Spring Boot applications.
exchange-db	Provides database entities and mappers for open and completed orders, trade records, user exchange accounts, balance-change history, and fee statistics.
exchange-rest	Runs the HTTP-facing exchange application, providing the application entry point and web configuration for exchange service APIs.
exchange-engine	Runs the matching engine application and initializes it at startup. It uses the matching logic in exchange-impl to process order and cancellation commands.
exchange-sync	Runs the data synchronization application. It uses shared Kafka listeners to process trade events and synchronize trade records with the database and Elasticsearch.


How to Run and Use Exchange
Exchange runs as a backend service. Client applications submit and cancel orders through the REST API, while the matching engine processes orders and downstream services consume the resulting events.
1. Set up the environment
Use Java 8 and Maven, and configure access to the project's internal com.weibi Maven dependencies. Set up Nacos for service discovery and configuration, then configure Kafka, Redis, the trade database, and Elasticsearch. The repository README lists the Kafka topics and the Nacos configuration required for each service.
2. Build and start the services
From the Maven project root, run mvn clean package. Start the three Spring Boot applications with their corresponding Nacos configurations:
Service	Spring Boot entry point	Role
exchange-engine	ExchangeEngineApplication	Processes order and cancellation commands.
exchange-sync	ExchangeSyncApplication	Consumes trade events and synchronizes trade records.
exchange-rest	ExchangeRestApplication	Exposes the API used by calling applications.


3. Test an order end to end
Use a configured trading pair and funded test accounts. The ExchangeService interface defines the request parameters for each endpoint.
1. Submit a limit order using POST /exchange/createOrderForUser.
2. Check the order book and open orders using GET /exchange/getQuotationOrdersDepth and GET /exchange/getTradeOrderUnfinishedList.
3. Submit a compatible order on the opposite side to test matching. Check the resulting Kafka trade event and the synchronized trade record.
4. Cancel a separate open order using POST /exchange/cancelOrderForUser. Check that the order is removed from the open-order list and that a cancellation event is published.
These steps are based on the repository’s README and service interfaces. Startup and API calls were not verified against a running environment.

start
1、添加kafkatopic：
    ./kafka-topics.sh --create --zookeeper 10.24.3.158:2181 --replication-factor 1 --partitions 54 --topic exchange-order-comm-v1
    ./kafka-topics.sh --create --zookeeper 10.24.3.158:2181 --replication-factor 1 --partitions 12 --topic exchange-order-hot-v1
    ./kafka-topics.sh --create --zookeeper 10.24.3.158:2181 --replication-factor 1 --partitions 1 --topic exchange-public-fee-v1
    ./kafka-topics.sh --create --zookeeper 10.24.3.158:2181 --replication-factor 1 --partitions 6 --topic exchange-plate-queue-v1
    ./kafka-topics.sh --zookeeper 10.24.3.158:2181 --alter --topic deal_made --partitions 3
    ./kafka-topics.sh --zookeeper 10.24.3.158:2181 --alter --topic order_filled --partitions 3
    ./kafka-topics.sh --zookeeper 10.24.3.158:2181 --alter --topic order_canceled --partitions 3
    ./kafka-topics.sh --zookeeper 10.24.3.158:2181 --alter --topic order_failed --partitions 3
    ./kafka-topics.sh --zookeeper 10.24.3.158:2181 --alter --topic BALANCE_RES_FROM_MATCH_ENGINE --partitions 3
    ./kafka-topics.sh --zookeeper 10.24.3.158:2181 --alter --topic order_added_to_queue --partitions 3
    ./kafka-topics.sh --zookeeper 10.24.3.158:2181 --alter --topic exchange-plate-queue-v1 --partitions 3
2、添加三个项目nacos配置
3、添加feign-servers.yaml   配置
    exchange-engine: exchange-engine
    exchange-rest: exchange-rest
    exchange-sync: exchange-sync
1、代码打包：feature/stage、feature/jsg_exchange_20220208
    tradeparent(合并到stage去掉完成订单落库) 推包 打包webapp发布
    account-service（去掉kafka和es消费，合并到stage） 推包，重启webapp
    common-tech-components（合并master） 推包
    config-service（合并master）  推包
    exchange
    bkex_api
    webapp（合并到stage 调用新撮合）
    bkex_admin  管理端
5、listener.listener_user_balance_change  webapp添加监听属性 
6、environment-properties.yaml
    newExchangePair: LTC_USDT   上线币种
    newOrOldExchangeRoute: false   是否开启老撮合

#KAFKA消息队列说明
TOPIC_EXCHANGE_ORDER_CREATED(exchange-order-created): 下单进入撮合队列
TOPIC_EXCHANGE_ORDER_CANCELED(exchange-order-canceled): 进入撤单队列
TOPIC_ORDER_FILLED(order_filled)：订单完成成交    
    订单完成写入ES(account-service),委托成功极光推送（webapp），写入数据库（tradeparent）
TOPIC_ORDER_CANCELED(order_canceled):订单取消    
    订单完成写入ES(account-service)，写入数据库（tradeparent），委托成功极光推送（webapp）
TOPIC_ORDER_FAILED(order_failed):订单失败    
    计划委托余额不足时发送消息
TOPIC_ORDER_ADDED_TO_QUEUE(order_added_to_queue):订单添加到撮合队列    
    订单完成写入ES(account-service)，
TOPIC_BALANCE_RES_INIT_FROM_MATCH_ENGINE(BALANCE_RES_FROM_MATCH_ENGINE):交易余额产生变动    
    socket推送用户(webapp)、资金变动钉钉提醒(webapp)，流水写入数据库和es(account-service)
    
#KAFKA消费组说明
exchangeOrderDefaultGroup:下单进入撮合消费组 批量消费 32线程 非自启动
    exchange-order
activeStopLimit：激活计划委托消费组  批量消费 3线程 非自启动
    deal_made
syncToDatacenterV1：数据写入es消费组  批量消费 6线程
    order_filled
    order_canceled
    order_failed
    BALANCE_RES_FROM_MATCH_ENGINE
    SUCC_BALANCE_HIST_WRITTEN
    deal_made
    general_deal
transMatchEngine: 余额记录数据写入DB消费组  批量消费 3线程
    BALANCE_RES_FROM_MATCH_ENGINE
    BALANCE_DELEGA_MANAGE_HISORY
saveTradeDeal：交易记录数据写入DB消费组  批量消费 3线程
    deal_made
saveFinishedOrder：完成订单记录数据写入DB消费组  批量消费 3线程
    order_filled
    order_canceled
    order_failed
    

#维护
1、数据库查询语句
    select * from  bkex_exchange.tbl_user_ex_account where id='WALLET-2021082415173364810000150-USDT' 
    select * from tbl_trade_order_unfinished where id = '2022031210515383310000002'
2、kafka查询
    unset JMX_PORT
    ./kafka-topics.sh --zookeeper 192.168.18.62:2181 --list
    ./kafka-topics.sh --zookeeper 192.168.18.62:2181 --describe --topic exchange-order
    #生产
    ./kafka-topics.sh --zookeeper 10.24.3.158:2181 --list
    ./kafka-topics.sh --zookeeper 10.24.3.158:2181 --describe --topic order_added_to_queue
    ./kafka-topics.sh --zookeeper 10.24.3.158:2181 --describe --topic exchange-order-comm-v1
    ./kafka-topics.sh --zookeeper 10.24.3.158:2181 --describe --topic exchange-order-hot-v1
    ./kafka-topics.sh --zookeeper 10.24.3.158:2181 --describe --topic deal_made
    ./kafka-topics.sh --zookeeper 10.24.3.158:2181 --describe --topic general_deal
    ./kafka-topics.sh --zookeeper 10.24.3.158:2181 --describe --topic order_filled
    ./kafka-topics.sh --zookeeper 10.24.3.158:2181 --describe --topic order_canceled
    ./kafka-topics.sh --zookeeper 10.24.3.158:2181 --describe --topic order_failed
    ./kafka-topics.sh --zookeeper 10.24.3.158:2181 --describe --topic BALANCE_DELEGA_MANAGE_HISORY
    ./kafka-topics.sh --zookeeper 10.24.3.158:2181 --describe --topic SUCC_BALANCE_HIST_WRITTEN
    ./kafka-topics.sh --zookeeper 10.24.3.158:2181 --describe --topic BALANCE_RES_FROM_MATCH_ENGINE
    ./kafka-topics.sh --zookeeper 10.24.3.158:2181 --describe --topic exchange-public-fee-v1
    ./kafka-topics.sh --zookeeper 10.24.3.158:2181 --describe --topic exchange-plate-queue-v1
    ./kafka-consumer-groups.sh --bootstrap-server localhost:9092 --list
    # [撮合队列]撮合下单撤单消费组 exchange-order
        ./kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group exchangeOrderDefaultGroupV1 --describe
        ./kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group exchangeOrderHotDefaultGroupV1 --describe
    # [ES]数据写入es消费组 订单 账单 成交记录 order_filled、deal_made、SUCC_BALANCE_HIST_WRITTEN、BALANCE_RES_FROM_MATCH_ENGINE
        ./kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group syncToDatacenterV1 --describe
    # [DB]激活计划委托消费组  deal_made
        ./kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group activeStopLimitV1 --describe
    # [DB]余额记录数据写入DB消费组 账单 BALANCE_DELEGA_MANAGE_HISORY、BALANCE_RES_FROM_MATCH_ENGINE
        ./kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group transMatchEngineV1 --describe
    # [DB]交易记录数据写入DB消费组 成交记录 deal_made
        ./kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group saveTradeDealV1 --describe
    # [DB]完成订单记录数据写入DB消费组 订单 order_filled、order_canceled、order_failed
        ./kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group saveFinishedOrderV1 --describe
    # [DB]手续费落库
        ./kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group dealMiningV1 --describe
    # [DB]手续费计算清单
        ./kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group factoryPublicFeeV1 --describe
    # [MEM]盘口数据推送
         ./kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group exchangePlateQueueV1_* --describe
   
    #老撮合 
    ./kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group activeStopLimit --describe
    ./kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group transMatchEngine --describe
    ./kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group saveFinishedOrder --describe
    ./kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group saveTradeDeal --describe
    ./kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group syncToDatacenter --describe
    
    # websocket 成交记录推送
    ./kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group consumerTradeOrderDealhera_1 --describe
    # websocket 下单或取消订单推送 order_added_to_queue
    ./kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group consumerTradeOrderAddToQuene+xx --describe
    ./kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group consumerTradeOrderAddToQuenehera_2 --describe
    # websocket 钱包推送
    ./kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group consumerNotifyWalletUpdate+xx --describe

3、redis-key计划委托
    止损队列(触发价<=市价)：trade:stop_limit:+pair+:lte
    止赢队列(触发价>=市价):trade:stop_limit:+pair+:gte
    用户计划委托数量:trade:stop_limit:+userId+:+pair    
    
4、机器人相关配置表
    select * from bkex_exchange.tbl_trade_pair_frequency:热币配置表
    select * from bkex_exchange.tbl_machine_static_config：现货比比交易机器人账户配置表
    select * from bkex_machine.tbl_etp_static_config：现货et交易机器人账户配置表
    
    
#下游推送数据
1./q/trade/quotation； http-get, 全量行情
2./asset/v5/coinLogoMa；http-get, 币种图标
3./p/config/pair/symbols； http-get, 获取所有交易对的配置信息
4./coinCurrency/anchoredCoins；http-get, 汇率接口
5./q/trade/depth， http-get, 获取盘口数据
6./p/config/pair/tip， http-get, 获取当前交易对提示内容
7./p/config/time/info，http-get, 获取当前交易对上币倒计时
8./u/trade/orders/list；http-get, 获取当前用户委托订单信息
9./u/user/getFeeProfile；http-get, 获取当前用户是否开启bkk折扣
10./u/user/queryOrderConfirm；http-get, 获取当前用户是否开启下单确认设置
11./u/user/setOrderConfirm； http-post, 设置当前用户下单确认
12./p/config/etp/relation；http-get, 获取当前交易对etp信息
13./pair/tradeConfig； http-get, 获取当前交易对交易费率信息
14./q/trade/kline; http-get, 获取当前交易对k线数据
15./u/cancel_trade_order_newV2；http-post; 撤销某个当前委托
16./u/cancel_trade_orders； http-post; 一键撤单
17./u/create_trade_order_newV2 ;http-post, 下单
18.subQuotationPair； socket-emit; 具体功能暂时不清楚，会在socket链接成功后调用；
19.moreSubKlineByRange; socket-emit; 订阅交易对k线数据
20.pairKlineByRange； socket-on; 监听后端返回的k线数据
21.qPairsKLine； socket-on;  监听后端返回的k线最后一根柱的数据，用于更新k线
22.quotationDealConnect；socket-emit; 订阅成交信息
23.quotationAllDeal； socket-on; 监听全量的成交信息
24.quotationListDeal； socket-on; 监听部分成交信息
25.subOrderDepth； socket-emit; 订阅盘口信息
26.quotationOrderDepth；socket-on; 全部盘口信息
27.qPriceQuantity； socket-on; 部分盘口信息
28.qPairsUpdateStats； socket-on; 部分行情数据 
29. userConnect； socket-emit; 用户切换交易对时，订阅用户信息
30.userTradeInfo； socket-on; 监听到此条消息，会触发获取当前用户、当前币种的委托信息    
