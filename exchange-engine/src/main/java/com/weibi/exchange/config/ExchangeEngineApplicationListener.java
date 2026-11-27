package com.weibi.exchange.config;

import com.weibi.core.commons.logging.ILogging;
import com.weibi.exchange.util.ExchangeKafkaUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * 项目启动初始化交易对
 *
 * @author jsong
 */
@Component
public class ExchangeEngineApplicationListener implements ApplicationListener<ApplicationReadyEvent>, ILogging {

    private static boolean INITIATED = false;
    @Autowired
    private ExchangeKafkaUtil exchangeKafkaUtil;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent applicationReadyEvent) {
        if (!INITIATED) {
            INITIATED = true;
        } else {
            return;
        }
        //for (int i=0;i<1;i++){
        //   exchangeKafkaUtil.doSendMsg("account-con-test", "sdfsdfsd", IdUtil.fastSimpleUUID());
        //}
        warn("撮合项目启动完成");
    }
}
