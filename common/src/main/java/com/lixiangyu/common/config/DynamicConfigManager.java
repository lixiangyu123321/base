package com.lixiangyu.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态配置管理器
 * 提供配置读取和监听功能
 *
 * @author lixiangyu
 */
@Slf4j
@Component
public class DynamicConfigManager implements ApplicationListener<EnvironmentChangeEvent> {

    @Autowired
    private Environment environment;

    /**
     * 配置缓存（用于快速访问）
     */
    private final Map<String, String> configCache = new ConcurrentHashMap<>();

    /**
     * 配置变更监听器列表
     */
    private final Map<String, ConfigChangeListener> listeners = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("动态配置管理器初始化完成");
    }

    /**
     * 获取配置值（String 类型）
     *
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    public String getString(String key, String defaultValue) {
        String value = configCache.get(key);
        if (value == null) {
            value = environment.getProperty(key, defaultValue);
            if (value != null) {
                configCache.put(key, value);
            }
        }
        return value;
    }

    /**
     * 获取配置值（String 类型，无默认值）
     *
     * @param key 配置键
     * @return 配置值，如果不存在返回 null
     */
    public String getString(String key) {
        return getString(key, null);
    }

    /**
     * 获取配置值（Integer 类型）
     *
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    public Integer getInteger(String key, Integer defaultValue) {
        String value = getString(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("配置项 {} 的值 {} 无法转换为 Integer，使用默认值 {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    /**
     * 获取配置值（Long 类型）
     *
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    public Long getLong(String key, Long defaultValue) {
        String value = getString(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("配置项 {} 的值 {} 无法转换为 Long，使用默认值 {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    /**
     * 获取配置值（Boolean 类型）
     *
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    public Boolean getBoolean(String key, Boolean defaultValue) {
        String value = getString(key);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    /**
     * 注册配置变更监听器
     *
     * @param key 配置键
     * @param listener 监听器
     */
    public void addListener(String key, ConfigChangeListener listener) {
        listeners.put(key, listener);
        log.info("注册配置变更监听器：{}", key);
    }

    /**
     * 移除配置变更监听器
     *
     * @param key 配置键
     */
    public void removeListener(String key) {
        listeners.remove(key);
        log.info("移除配置变更监听器：{}", key);
    }

    /**
     * 监听配置变更事件
     */
    @Override
    public void onApplicationEvent(EnvironmentChangeEvent event) {
        Set<String> changedKeys = event.getKeys();
        log.info("检测到配置变更，变更的配置项：{}", changedKeys);

        for (String key : changedKeys) {
            // 清除缓存
            configCache.remove(key);

            // 获取新值
            String newValue = environment.getProperty(key);
            if (newValue != null) {
                configCache.put(key, newValue);
            }

            // 通知监听器
            ConfigChangeListener listener = listeners.get(key);
            if (listener != null) {
                try {
                    listener.onChange(key, newValue);
                } catch (Exception e) {
                    log.error("配置变更监听器执行失败：{}", key, e);
                }
            }
        }
    }

    /**
     * 配置变更监听器接口
     */
    @FunctionalInterface
    public interface ConfigChangeListener {
        /**
         * 配置变更回调
         *
         * @param key 配置键
         * @param newValue 新值
         */
        void onChange(String key, String newValue);
    }
}
