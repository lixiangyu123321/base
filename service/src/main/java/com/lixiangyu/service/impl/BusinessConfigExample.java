package com.lixiangyu.service.impl;

import com.lixiangyu.common.config.DynamicConfigManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * 业务配置示例
 * 演示如何使用配置中心读取配置和监听配置变更
 *
 * @author lixiangyu
 */
@Slf4j
@Component
@RefreshScope  // 支持配置热更新
public class BusinessConfigExample {

    @Autowired
    private DynamicConfigManager configManager;

    /**
     * 批次大小配置（从配置中心读取）
     */
    private Integer batchSize;

    /**
     * 超时时间配置（从配置中心读取）
     */
    private Long timeout;

    @PostConstruct
    public void init() {
        // 初始化时读取配置
        loadConfig();

        // 注册配置变更监听器
        configManager.addListener("business.batch.size", (key, newValue) -> {
            log.info("批次大小配置已更新：{} = {}", key, newValue);
            this.batchSize = Integer.parseInt(newValue);
            // 可以在这里添加业务逻辑，例如重新初始化相关组件
        });

        configManager.addListener("business.batch.timeout", (key, newValue) -> {
            log.info("超时时间配置已更新：{} = {}", key, newValue);
            this.timeout = Long.parseLong(newValue);
        });
    }

    /**
     * 加载配置
     */
    private void loadConfig() {
        // 从配置中心读取配置，如果不存在则使用默认值
        this.batchSize = configManager.getInteger("business.batch.size", 1000);
        this.timeout = configManager.getLong("business.batch.timeout", 30000L);
        
        log.info("业务配置加载完成：batchSize={}, timeout={}", batchSize, timeout);
    }

    /**
     * 获取批次大小
     */
    public Integer getBatchSize() {
        // 每次获取时重新读取（确保获取最新值）
        return configManager.getInteger("business.batch.size", batchSize);
    }

    /**
     * 获取超时时间
     */
    public Long getTimeout() {
        // 每次获取时重新读取（确保获取最新值）
        return configManager.getLong("business.batch.timeout", timeout);
    }
}

