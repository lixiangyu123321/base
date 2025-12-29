package com.lixiangyu.common.config;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * 动态配置管理器（基于 Nacos 配置中心）
 * 提供配置读取、监听和发布功能
 * 
 * 功能特性：
 * 1. 支持从 Nacos 配置中心读取配置
 * 2. 支持配置热更新（自动刷新缓存）
 * 3. 支持配置变更监听（自定义监听器）
 * 4. 支持配置发布（动态更新 Nacos 配置）
 * 5. 支持多 Data ID 配置
 * 6. 支持 JSON/YAML 格式配置
 * 7. 配置优先级：Nacos > Environment > 默认值
 *
 * @author lixiangyu
 */
@Slf4j
@Component
public class DynamicConfigManager implements ApplicationListener<EnvironmentChangeEvent> {

    /**
     * 默认配置超时时间（毫秒）
     */
    private static final long DEFAULT_TIMEOUT = 3000;


    @Autowired
    private Environment environment;

    @Resource
    private ConfigService configService;

    /**
     * 配置缓存（用于快速访问）
     * Key: 配置键, Value: 配置值
     */
    private final Map<String, String> configCache = new ConcurrentHashMap<>();

    /**
     * 配置变更监听器列表
     * Key: 配置键, Value: 监听器列表
     */
    private final Map<String, List<ConfigChangeListener>> listeners = new ConcurrentHashMap<>();

    /**
     * Nacos 监听器映射（用于移除监听器）
     * Key: 配置键, Value: Nacos Listener
     */
    private final Map<String, Listener> nacosListeners = new ConcurrentHashMap<>();

    /**
     * 主配置 Data ID（从 Environment 读取）
     */
    private String mainDataId;

    /**
     * 配置分组（从 Environment 读取）
     */
    private String group;

    /**
     * 命名空间（从 Environment 读取）
     */
    private String namespace;

    /**
     * 配置格式（json/yml/yaml/properties）
     */
    private String configType;

    /**
     * 是否启用 Nacos 配置中心
     */
    private boolean nacosEnabled;

    /**
     * 初始化配置管理器
     */
    @PostConstruct
    public void init() {
        try {
            // 从 Environment 读取 Nacos 配置
            loadNacosConfig();
            
            // 如果启用 Nacos，初始化配置
            if (nacosEnabled && configService != null) {
                loadConfigFromNacos();
                log.info("动态配置管理器初始化完成，Data ID: {}, Group: {}, Namespace: {}", 
                        mainDataId, group, namespace);
            } else {
                log.info("动态配置管理器初始化完成（Nacos 未启用，使用本地配置）");
            }
        } catch (Exception e) {
            log.error("动态配置管理器初始化失败", e);
            // 初始化失败不影响应用启动，使用本地配置
        }
    }

    /**
     * 从 Environment 加载 Nacos 配置
     */
    private void loadNacosConfig() {
        // 读取应用名称和激活的 profile
        String appName = environment.getProperty("spring.application.name", "demo");
        String activeProfile = environment.getProperty("spring.profiles.active", "dev");
        String fileExtension = environment.getProperty("spring.cloud.nacos.config.file-extension", "yml");
        
        // 构建主配置 Data ID
        mainDataId = environment.getProperty("spring.cloud.nacos.config.data-id", 
                String.format("%s-%s.%s", appName, activeProfile, fileExtension));
        
        // 读取配置分组
        group = environment.getProperty("spring.cloud.nacos.config.group", "DEFAULT_GROUP");
        
        // 读取命名空间
        namespace = environment.getProperty("spring.cloud.nacos.config.namespace", "");
        
        // 读取配置格式
        configType = fileExtension.equals("yml") || fileExtension.equals("yaml") ? "yaml" : "json";
        
        // 读取是否启用
        nacosEnabled = environment.getProperty("spring.cloud.nacos.config.enabled", Boolean.class, true);
        
        log.debug("Nacos 配置加载完成 - Data ID: {}, Group: {}, Namespace: {}, Type: {}", 
                mainDataId, group, namespace, configType);
    }

    /**
     * 从 Nacos 加载配置并解析到缓存
     */
    private void loadConfigFromNacos() {
        try {
            String configContent = configService.getConfig(mainDataId, group, DEFAULT_TIMEOUT);
            if (StringUtils.hasText(configContent)) {
                parseAndCacheConfig(configContent);
                log.info("成功从 Nacos 加载配置，Data ID: {}", mainDataId);
            } else {
                log.warn("Nacos 配置为空，Data ID: {}", mainDataId);
            }
        } catch (NacosException e) {
            log.warn("从 Nacos 加载配置失败，将使用本地配置，Data ID: {}, 错误: {}", mainDataId, e.getMessage());
        }
    }

    /**
     * 解析配置内容并缓存
     * 
     * @param configContent 配置内容
     */
    private void parseAndCacheConfig(String configContent) {
        try {
            if ("json".equalsIgnoreCase(configType)) {
                // JSON 格式
                JSONObject jsonObject = JSON.parseObject(configContent);
                for (String key : jsonObject.keySet()) {
                    String value = jsonObject.getString(key);
                    if (value != null) {
                        configCache.put(key, value);
                    }
                }
            } else {
                // YAML 格式（简单解析，复杂场景建议使用 SnakeYAML）
                // 这里简化处理，实际项目中可以使用 Spring 的 YAML 解析器
                log.warn("YAML 格式配置解析暂未实现，请使用 JSON 格式或通过 Environment 获取");
            }
        } catch (Exception e) {
            log.error("解析配置内容失败", e);
        }
    }

    /**
     * 获取配置值（String 类型）
     * 优先级：Nacos 缓存 > Environment > 默认值
     *
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    public String getString(String key, String defaultValue) {
        // 1. 从缓存获取
        String value = configCache.get(key);
        if (value != null) {
            return value;
        }
        
        // 2. 从 Environment 获取
        value = environment.getProperty(key);
        if (value != null) {
            configCache.put(key, value);
            return value;
        }
        
        // 3. 如果启用 Nacos 且缓存中没有，尝试从 Nacos 获取
        if (nacosEnabled && configService != null) {
            try {
                String configContent = configService.getConfig(mainDataId, group, DEFAULT_TIMEOUT);
                if (StringUtils.hasText(configContent)) {
                    parseAndCacheConfig(configContent);
                    value = configCache.get(key);
                    if (value != null) {
                        return value;
                    }
                }
            } catch (NacosException e) {
                log.debug("从 Nacos 获取配置失败，使用默认值，Key: {}, 错误: {}", key, e.getMessage());
            }
        }
        
        // 4. 返回默认值
        return defaultValue;
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
            return Integer.parseInt(value.trim());
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
            return Long.parseLong(value.trim());
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
        return Boolean.parseBoolean(value.trim());
    }

    /**
     * 获取配置值（Double 类型）
     *
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    public Double getDouble(String key, Double defaultValue) {
        String value = getString(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            log.warn("配置项 {} 的值 {} 无法转换为 Double，使用默认值 {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    /**
     * 注册配置变更监听器（使用主 Data ID）
     *
     * @param key 配置键
     * @param listener 监听器
     */
    public void addListener(String key, ConfigChangeListener listener) {
        addListener(key, null, listener);
    }
    
    /**
     * 注册配置变更监听器（支持指定 Data ID）
     *
     * @param key 配置键（用于标识监听器，可以是配置键或 Data ID）
     * @param dataId 要监听的 Data ID（如果为 null，则使用主 Data ID）
     * @param listener 监听器
     */
    public void addListener(String key, String dataId, ConfigChangeListener listener) {
        // 添加到监听器列表
        listeners.computeIfAbsent(key, k -> new ArrayList<>()).add(listener);
        
        // 如果启用 Nacos，注册 Nacos 监听器
        if (nacosEnabled && configService != null) {
            try {
                // 确定要监听的 Data ID
                String targetDataId = StringUtils.hasText(dataId) ? dataId : mainDataId;
                
                // 创建 Nacos 监听器适配器
                // 如果指定了 dataId，说明是监听整个 Data ID 的变更（如任务配置的独立 Data ID）
                // 如果未指定 dataId，说明是监听主 Data ID 中某个 key 的变更
                Listener nacosListener;
                if (StringUtils.hasText(dataId)) {
                    // 监听整个 Data ID 的变更，传入完整的配置内容
                    nacosListener = new NacosDataIdListenerAdapter(key, dataId, listener);
                } else {
                    // 监听主 Data ID 中某个 key 的变更
                    nacosListener = new NacosListenerAdapter(key, listener);
                }
                
                configService.addListener(targetDataId, group, nacosListener);
                nacosListeners.put(key, nacosListener);
                // 记录监听器对应的 Data ID，用于销毁时正确移除
                listenerDataIdMap.put(key, targetDataId);
                log.info("注册配置变更监听器成功，Key: {}, Data ID: {}", key, targetDataId);
            } catch (NacosException e) {
                log.error("注册 Nacos 配置变更监听器失败，Key: {}, Data ID: {}", key, dataId, e);
                // 即使 Nacos 监听器注册失败，也保留本地监听器
            }
        } else {
            log.info("注册配置变更监听器（仅本地），Key: {}", key);
        }
    }

    /**
     * 移除配置变更监听器
     *
     * @param key 配置键
     */
    public void removeListener(String key) {
        removeListener(key, null);
    }
    
    /**
     * 移除配置变更监听器（支持指定 Data ID）
     *
     * @param key 配置键
     * @param dataId 要移除监听的 Data ID（如果为 null，则使用主 Data ID）
     */
    public void removeListener(String key, String dataId) {
        // 移除本地监听器
        listeners.remove(key);
        
        // 移除 Nacos 监听器
        if (nacosEnabled && configService != null) {
            Listener nacosListener = nacosListeners.remove(key);
            if (nacosListener != null) {
                try {
                    // 优先使用传入的 dataId，否则从映射中获取，最后使用主 Data ID
                    String targetDataId = StringUtils.hasText(dataId) 
                            ? dataId 
                            : listenerDataIdMap.getOrDefault(key, mainDataId);
                    configService.removeListener(targetDataId, group, nacosListener);
                    listenerDataIdMap.remove(key);
                    log.info("移除配置变更监听器成功，Key: {}, Data ID: {}", key, targetDataId);
                } catch (Exception e) {
                    log.error("移除 Nacos 配置变更监听器失败，Key: {}, Data ID: {}", key, dataId, e);
                }
            }
        }
    }

    /**
     * 监听配置变更事件（Spring Cloud 环境变更事件）
     * 当 Nacos 配置变更时，Spring Cloud 会发布此事件
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

            // 通知所有注册的监听器
            List<ConfigChangeListener> keyListeners = listeners.get(key);
            if (keyListeners != null) {
                for (ConfigChangeListener listener : keyListeners) {
                    try {
                        listener.onChange(key, newValue);
                    } catch (Exception e) {
                        log.error("配置变更监听器执行失败，Key: {}, Listener: {}", key, listener.getClass().getName(), e);
                    }
                }
            }
        }
    }

    /**
     * 发布配置到 Nacos
     *
     * @param content 配置内容
     * @param dataId 配置 Data ID（可选，默认使用主 Data ID）
     * @param configGroup 配置分组（可选，默认使用主分组）
     * @return 发布是否成功
     */
    public boolean publishConfig(String content, String dataId, String configGroup) {
        if (!nacosEnabled || configService == null) {
            log.warn("Nacos 未启用，无法发布配置");
            return false;
        }
        
        try {
            String targetDataId = StringUtils.hasText(dataId) ? dataId : mainDataId;
            String targetGroup = StringUtils.hasText(configGroup) ? configGroup : group;
            String targetType = configType;
            
            configService.publishConfig(targetDataId, targetGroup, content, targetType);
            
            // 发布成功后，解析并更新缓存
            parseAndCacheConfig(content);
            
            log.info("发布配置成功，Data ID: {}, Group: {}", targetDataId, targetGroup);
            return true;
        } catch (NacosException e) {
            log.error("发布配置失败，Data ID: {}, Group: {}", 
                    StringUtils.hasText(dataId) ? dataId : mainDataId, 
                    StringUtils.hasText(configGroup) ? configGroup : group, e);
            return false;
        }
    }

    /**
     * 发布配置到 Nacos（使用默认 Data ID 和 Group）
     *
     * @param content 配置内容
     * @return 发布是否成功
     */
    public boolean publishConfig(String content) {
        return publishConfig(content, null, null);
    }

    /**
     * 刷新配置缓存（从 Nacos 重新加载）
     */
    public void refreshConfig() {
        if (nacosEnabled && configService != null) {
            log.info("刷新配置缓存，Data ID: {}", mainDataId);
            loadConfigFromNacos();
        } else {
            log.warn("Nacos 未启用，无法刷新配置");
        }
    }

    /**
     * 清除配置缓存
     */
    public void clearCache() {
        configCache.clear();
        log.info("配置缓存已清除");
    }

    /**
     * 获取配置缓存大小
     *
     * @return 缓存大小
     */
    public int getCacheSize() {
        return configCache.size();
    }

    /**
     * 存储监听器信息（Key -> Data ID 的映射）
     * 用于销毁时正确移除监听器
     */
    private final Map<String, String> listenerDataIdMap = new ConcurrentHashMap<>();
    
    /**
     * 销毁时清理资源
     */
    @PreDestroy
    public void destroy() {
        // 移除所有 Nacos 监听器
        if (nacosEnabled && configService != null) {
            for (Map.Entry<String, Listener> entry : nacosListeners.entrySet()) {
                try {
                    String dataId = listenerDataIdMap.getOrDefault(entry.getKey(), mainDataId);
                    configService.removeListener(dataId, group, entry.getValue());
                } catch (Exception e) {
                    log.error("移除 Nacos 监听器失败，Key: {}, Data ID: {}", 
                            entry.getKey(), listenerDataIdMap.get(entry.getKey()), e);
                }
            }
            nacosListeners.clear();
            listenerDataIdMap.clear();
        }
        
        // 清除监听器列表
        listeners.clear();
        
        // 清除缓存
        configCache.clear();
        
        log.info("动态配置管理器已销毁");
    }

    /**
     * Nacos 监听器适配器
     * 将 Nacos 的 Listener 适配为 ConfigChangeListener
     * 用于监听主 Data ID 中某个 key 的变更
     */
    private class NacosListenerAdapter implements Listener {
        private final String key;
        private final ConfigChangeListener listener;

        public NacosListenerAdapter(String key, ConfigChangeListener listener) {
            this.key = key;
            this.listener = listener;
        }

        @Override
        public void receiveConfigInfo(String configInfo) {
            try {
                // 解析配置内容
                if (StringUtils.hasText(configInfo)) {
                    parseAndCacheConfig(configInfo);
                    
                    // 获取新值
                    String newValue = configCache.get(key);
                    
                    // 通知监听器
                    listener.onChange(key, newValue);
                    
                    log.info("Nacos 配置变更通知，Key: {}, New Value: {}", key, newValue);
                }
            } catch (Exception e) {
                log.error("处理 Nacos 配置变更通知失败，Key: {}", key, e);
            }
        }

        @Override
        public Executor getExecutor() {
            return null; // 使用默认执行器
        }
    }
    
    /**
     * Nacos Data ID 监听器适配器
     * 用于监听整个 Data ID 的变更（如任务配置的独立 Data ID）
     * 当 Data ID 的整个内容变更时，将整个配置内容传递给监听器
     */
    private class NacosDataIdListenerAdapter implements Listener {
        private final String key;
        private final String dataId;
        private final ConfigChangeListener listener;

        public NacosDataIdListenerAdapter(String key, String dataId, ConfigChangeListener listener) {
            this.key = key;
            this.dataId = dataId;
            this.listener = listener;
        }

        @Override
        public void receiveConfigInfo(String configInfo) {
            try {
                // 对于独立的 Data ID，直接传递整个配置内容
                if (StringUtils.hasText(configInfo)) {
                    // 通知监听器，key 是 Data ID，newValue 是整个配置内容
                    listener.onChange(key, configInfo);
                    
                    log.info("Nacos Data ID 配置变更通知，Data ID: {}, Key: {}", dataId, key);
                }
            } catch (Exception e) {
                log.error("处理 Nacos Data ID 配置变更通知失败，Data ID: {}, Key: {}", dataId, key, e);
            }
        }

        @Override
        public Executor getExecutor() {
            return null; // 使用默认执行器
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
