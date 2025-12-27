package com.lixiangyu.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Nacos 配置监听器
 * 监听配置中心的配置变更，实现配置热更新
 *
 * @author lixiangyu
 */
@Slf4j
@Component
public class NacosConfigListener implements ApplicationListener<EnvironmentChangeEvent> {

    /**
     * 监听 Spring Cloud 的环境变更事件
     * 当 Nacos 配置更新时，Spring Cloud 会发布此事件
     */
    @Override
    public void onApplicationEvent(EnvironmentChangeEvent event) {
        Set<String> changedKeys = event.getKeys();
        log.info("检测到配置变更，变更的配置项：{}", changedKeys);
        
        // 处理配置变更
        for (String key : changedKeys) {
            handleConfigChange(key);
        }
    }

    /**
     * 处理单个配置项的变更
     *
     * @param key 配置项的 key
     */
    private void handleConfigChange(String key) {
        log.info("配置项 {} 已更新", key);
        
        // 根据配置项的 key 执行相应的处理逻辑
        // 例如：重新加载 Bean、更新缓存等
        
        // 示例：如果更新了线程池配置，可以重新初始化线程池
        if (key.startsWith("thread.pool.")) {
            log.info("线程池配置已更新，需要重新初始化线程池");
            // 可以在这里添加重新初始化线程池的逻辑
        }
        
        // 示例：如果更新了数据库配置，可以重新初始化数据源
        if (key.startsWith("spring.datasource.")) {
            log.warn("数据库配置已更新，但数据源通常需要重启应用才能生效");
        }
        
        // 示例：如果更新了业务配置，可以通知相关服务
        if (key.startsWith("business.")) {
            log.info("业务配置已更新：{}", key);
            // 可以在这里添加业务逻辑处理
        }
    }
}
