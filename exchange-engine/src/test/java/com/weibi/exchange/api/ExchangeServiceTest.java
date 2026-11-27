package com.weibi.exchange.api;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.http.HttpUtil;
import io.swagger.annotations.ApiParam;
import org.junit.Test;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

public class ExchangeServiceTest {
    private static final String DOMAIN = "http://localhost:8081/";
    private static final String DOMAIN_ENGINE = "http://localhost:8082/";
    private static final String DOMAIN_ENGINE_STAGE = "http://192.168.22.28:8148/";
    private static final String DOMAIN_ENGINE_ONLINE = "http://192.168.18.216:8148/";

    /**
     * 限价买
     */
    @Test
    public void createOrderForUserBidLimit() {
        //int ii = Integer.MAX_VALUE;
        int ii = 1;
        for (int i = 0; i < ii; i++) {
            Map<String, Object> param = new HashMap<>();
            param.put("userId", "2021082415173364810000150");
            param.put("pair", "LTC_USDT");
            param.put("direction", "BID");
            param.put("amount", "1");
            param.put("orderType", "LIMIT");
            //param.put("price", (new Random().nextInt(10)+1));
            param.put("price", 2);
            String result = HttpUtil.post(DOMAIN + "exchange/createOrderForMachine", param);
            System.out.println(result);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 市价买
     */
    @Test
    public void createOrderForUserBidMarket() {
        Map<String, Object> param = new HashMap<>();
        param.put("userId", "2021082415173364810000150");
        param.put("pair", "LTC_USDT");
        param.put("direction", "BID");
        param.put("amount", "1");
        param.put("orderType", "MARKET");
        String result = HttpUtil.post(DOMAIN + "exchange/createOrderForUser", param);
        System.out.println(result);
    }

    /**
     * 委托买
     */
    @Test
    public void createOrderForUserBidStopLimit() {
        Map<String, Object> param = new HashMap<>();
        param.put("userId", "2021082415173364810000150");
        param.put("pair", "LTC_USDT");
        param.put("direction", "BID");
        param.put("amount", "1");
        param.put("orderType", "STOP_LIMIT");
        param.put("price", "4");
        param.put("stopPrice", "3");
        param.put("operator", "gte");
        String result = HttpUtil.post(DOMAIN + "exchange/createOrderForUser", param);
        System.out.println(result);
    }

    /**
     * 限价卖
     */
    @Test
    public void createOrderForUserAskLimit() {
        //int ii = Integer.MAX_VALUE;
        int ii = 1;
        for (int i = 0; i < ii; i++) {
            Map<String, Object> param = new HashMap<>();
            param.put("userId", "2021082315363826268000106");
            param.put("pair", "LTC_USDT");
            param.put("direction", "ASK");
            param.put("amount", "1");
            param.put("orderType", "LIMIT");
            //param.put("price", (new Random().nextInt(10)+1));
            param.put("price", 2);
            String result = HttpUtil.post(DOMAIN + "exchange/createOrderForUser", param);
            System.out.println(result);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 市价卖
     */
    @Test
    public void createOrderForUserAskMarket() {
        Map<String, Object> param = new HashMap<>();
        param.put("userId", "2021082315363826268000106");
        param.put("pair", "LTC_USDT");
        param.put("direction", "ASK");
        param.put("amount", "1");
        param.put("orderType", "MARKET");
        String result = HttpUtil.post(DOMAIN + "exchange/createOrderForUser", param);
        System.out.println(result);
    }

    /**
     * 委托卖
     */
    @Test
    public void createOrderForUserAskStopLimit() {
        Map<String, Object> param = new HashMap<>();
        param.put("userId", "2021082315363826268000106");
        param.put("pair", "LTC_USDT");
        param.put("direction", "ASK");
        param.put("amount", "0.2");
        param.put("orderType", "STOP_LIMIT");
        param.put("price", "199");
        param.put("stopPrice", "");
        param.put("operator", "");
        String result = HttpUtil.post(DOMAIN + "exchange/createOrderForUser", param);
        System.out.println(result);
    }

    /**
     * 撤销
     */
    @Test
    public void cancelOrderForUser() {
        List<String> orderNoList = new ArrayList<>();
        orderNoList.add("2022101917195789410000001");
        for (String orderNo : orderNoList) {
            Map<String, Object> param = new HashMap<>();
            param.put("userId", "2021082415173364810000150");
            param.put("orderNo", orderNo);
            param.put("pair", orderNo);
            String result = HttpUtil.post(DOMAIN + "exchange/cancelOrderForUser", param);
            System.out.println(result);
        }
    }

    /**
     * 模拟成交 触发计划委托
     */
    @Test
    public void activeStopLimitDeal() {
        Map<String, Object> param = new HashMap<>();
        param.put("pair", "LTC_USDT");
        param.put("direction", "B");
        param.put("amount", "1");
        param.put("price", "2");
        String result = HttpUtil.post(DOMAIN + "exchange/activeStopLimitDeal", param);
        System.out.println(result);
    }

    /**
     * 网关身份认证请求
     */
    @Test
    public void gatewayIdTest() {
        //String requestPath = "/api/test";
        //String stime = String.valueOf(System.currentTimeMillis());
        //String imei = IdUtil.fastSimpleUUID();
        String requestPath = "/api/v2/commons/bannerList";
        String stime = "1656055247215";
        String imei = "5692b6522d234f2cb195d9e4ae1a6ebd";
        String md5 = SecureUtil.md5(String.format("%s:%s:%s:%s", "asd26xzcv@$12", requestPath, stime, imei));
        System.out.println(md5);
        Map<String, String> headerMap = new HashMap<>();
        headerMap.put("stime", stime);
        headerMap.put("imei", imei);
        headerMap.put("ssign", "f619d38752a3ad0380b250aab8fc966e");
        String body = HttpUtil.createGet("http://localhost:8888/api/test")
                .headerMap(headerMap, true)
                .execute().body();
        System.out.println(body);
    }

    /**
     * 限价买
     */
    @Test
    public void createOrderForUserBidLimitAll() {
        //int ii = Integer.MAX_VALUE;
        int ii = 3;
        for (int i = 0; i < ii; i++) {
            Map<String, Object> param = new HashMap<>();
            param.put("userId", "2021082415173364810000150");
            param.put("pair", "LTC_USDT");
            param.put("direction", "BID");
            param.put("amount", "1");
            param.put("orderType", "LIMIT");
            param.put("price", "2");
            String result = HttpUtil.post(DOMAIN + "exchange/createOrderForMachine", param);
            System.out.println(result);
        }

        ii = 3;
        for (int i = 0; i < ii; i++) {
            Map<String, Object> param = new HashMap<>();
            param.put("userId", "2021082415173364810000150");
            param.put("pair", "LTC_USDT");
            param.put("direction", "ASK");
            param.put("amount", "1");
            param.put("orderType", "LIMIT");
            param.put("price", "2");
            String result = HttpUtil.post(DOMAIN + "exchange/createOrderForUser", param);
            System.out.println(result);
        }

        ii = 0;
        for (int i = 0; i < ii; i++) {
            Map<String, Object> param = new HashMap<>();
            param.put("userId", "2021082315363826268000106");
            param.put("pair", "LTC_USDT");
            param.put("direction", "ASK");
            param.put("amount", "1");
            param.put("orderType", "LIMIT");
            param.put("price", "2");
            String result = HttpUtil.post(DOMAIN + "exchange/createOrderForUser", param);
            System.out.println(result);
        }
    }

    @Test
    public void getQuotationOrdersDepthForDb() {
        Map<String, Object> param = new HashMap<>();
        param.put("pair", "LTC_USDT");
        param.put("orderSizeAfterMerged", "200");
        param.put("amountPrecision", "8");
        param.put("pricePrecision", "8");
        String result = HttpUtil.get(DOMAIN_ENGINE + "exchange-engine/getQuotationOrdersDepthForDb", param);
        System.out.println(result);
    }

    @Test
    public void getQuotationOrdersDepthForMem() {
        Map<String, Object> param = new HashMap<>();
        param.put("pair", "XRP3S_USDT");
        param.put("orderSizeAfterMerged", "200");
        param.put("amountPrecision", "8");
        param.put("pricePrecision", "8");
        String result = HttpUtil.get(DOMAIN_ENGINE_STAGE + "exchange-engine/getQuotationOrdersDepthForMem", param);
        System.out.println(result);
    }

    @Test
    public void getPairExchangeStatistics() {
        Map<String, Object> param = new HashMap<>();
        param.put("pair", "XRP3S_USDT");
        String result = HttpUtil.get(DOMAIN_ENGINE_STAGE + "exchange-engine/getPairExchangeStatistics", param);
        //String result = HttpUtil.get(DOMAIN_ENGINE_ONLINE+ "exchange-engine/getPairExchangeStatistics", param);
        System.out.println(result);
    }

    @Test
    public void getExchangeMemStatistics() {
        Map<String, Object> param = new HashMap<>();
        param.put("pair", "BTC_USDT");
        //String result = HttpUtil.get(DOMAIN + "exchange-engine/getExchangeMemStatistics", param);
        String result = HttpUtil.get(DOMAIN_ENGINE_STAGE + "exchange-engine/getExchangeMemStatistics", param);
        //String result = HttpUtil.get(DOMAIN_ENGINE_ONLINE + "exchange-engine/getExchangeMemStatistics", param);
        System.out.println(result);
    }

    @Test
    public void findOrderById() {
        Map<String, Object> param = new HashMap<>();
        param.put("pair", "XRP3S_USDT");
        param.put("id", "2022101717255889110062361");
        //String result = HttpUtil.get(DOMAIN + "exchange-engine/getExchangeMemStatistics", param);
        String result = HttpUtil.get(DOMAIN_ENGINE_STAGE + "exchange-engine/findOrderById", param);
        //String result = HttpUtil.get(DOMAIN_ENGINE_ONLINE + "exchange-engine/getExchangeMemStatistics", param);
        System.out.println(result);
    }

    @Test
    public void selectOne() {
        Map<String, Object> param = new HashMap<>();
        param.put("id", "111");
        //String result = HttpUtil.get(DOMAIN + "exchange-engine/getExchangeMemStatistics", param);
        String result = HttpUtil.get(DOMAIN_ENGINE_STAGE + "exchange-engine/selectOne", param);
        //String result = HttpUtil.get(DOMAIN_ENGINE_ONLINE + "exchange-engine/getExchangeMemStatistics", param);
        System.out.println(result);
    }

    @Test
    public void selectOne11() {
        Map<String, Object> param = new HashMap<>();
        //param.put("pair", "BTC_USDT");
        //String result = HttpUtil.get(DOMAIN + "exchange-engine/getExchangeMemStatistics", param);
        String result = HttpUtil.createGet(DOMAIN_ENGINE_STAGE + "exchange-engine/selectOne")
                .header("","")
                .execute()
                .body();
        //String result = HttpUtil.get(DOMAIN_ENGINE_ONLINE + "exchange-engine/getExchangeMemStatistics", param);
        System.out.println(result);
    }

    @Test
    public void test11() throws UnknownHostException {
        System.out.println(java.security.Security.getProperty("networkaddress.cache.ttl"));
        System.out.println(java.security.Security.getProperty("networkaddress.cache.ttl"));

        InetAddress byName = InetAddress.getByName("www.baidu.com");
        System.out.println(byName.getAddress());
        System.out.println(byName.getHostAddress());

    }
}
