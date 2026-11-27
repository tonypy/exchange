package com.weibi.exchange.config;

import com.weibi.core.commons.db.util.DataSourceFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.annotation.Resource;

/**
 * Db配置
 * @author huopengbo
 * @version 1.0
 * @date 2020-05-07 10:39
 */
@Configuration
public class DbConfiguration {

    @Resource
    private DataSourceFactory dataSourceFactory;
    @Resource
    private JdbcTemplate shardingJdbcTemplate;

    @Bean(name = "bizMasterTemplate")
    public JdbcTemplate getBizMasterTemplate(){
        return dataSourceFactory.generateTemplate("bizMaster");
    }

    @Bean(name = "bizSlaveTemplate")
    public JdbcTemplate getBizSlaveTemplate(){
        return dataSourceFactory.generateTemplate("bizSlave1");
    }

    @Bean(name = "bizSlave2Template")
    public JdbcTemplate getBizSlave2Template(){
        return dataSourceFactory.generateTemplate("bizSlave2");
    }

    @Bean(name = "bizMasterNamedParamTemplate")
    public NamedParameterJdbcTemplate bizMasterNamedParamTemplate() {
        return new NamedParameterJdbcTemplate(shardingJdbcTemplate);
    }
}
