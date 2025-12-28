package com.lixiangyu.config;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

@Slf4j
public class NacosConfigService {

    private static final String SERVER_ADDR = "127.0.0.1:8848";
    private static final String DATA_ID = "demo-dev.json";
    private static final String GROUP = "DEFAULT_GROUP";
    private static final long timeout = 3000;

    @Bean
    public ConfigService nacosConfigService(){
        try {
            Properties properties = new Properties();
            properties.put("serverAddr", SERVER_ADDR);
            ConfigService configService = NacosFactory.createConfigService(properties);
            return configService;
        } catch (NacosException e) {
            log.error("", e);
            return null;
        }
    }
}
