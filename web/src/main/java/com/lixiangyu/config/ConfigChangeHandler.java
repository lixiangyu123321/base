package com.lixiangyu.config;

import com.lixiangyu.common.config.DynamicConfigManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * 配置变更处理器
 * 示例：演示如何监听和处理配置变更
 *
 * @author lixiangyu
 */
@Slf4j
@Component
public class ConfigChangeHandler {

    @Autowired
    private DynamicConfigManager dynamicConfigManager;

    @PostConstruct
    public void init() {
        // 注册配置变更监听器示例
        
        // 监听业务配置
        dynamicConfigManager.addListener("business.batch.size", (key, newValue) -> {
            log.info("业务批次大小配置已更新：{} = {}", key, newValue);
            // 可以在这里更新业务逻辑
        });

        // 监听线程池配置
        dynamicConfigManager.addListener("thread.pool.core.size", (key, newValue) -> {
            log.info("线程池核心线程数配置已更新：{} = {}", key, newValue);
            // 注意：线程池配置通常需要重启应用才能生效
        });

        // 监听开关配置
        dynamicConfigManager.addListener("feature.enabled", (key, newValue) -> {
            log.info("功能开关配置已更新：{} = {}", key, newValue);
            Boolean enabled = Boolean.parseBoolean(newValue);
            if (enabled) {
                log.info("功能已启用");
            } else {
                log.info("功能已禁用");
            }
        });
    }
}

