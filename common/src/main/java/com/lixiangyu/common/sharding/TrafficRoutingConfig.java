package com.lixiangyu.common.sharding;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.lixiangyu.common.config.DynamicConfigManager;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 流量路由配置管理器
 * 支持基于规则的路由策略，实现读写分离、分库分表路由等
 * 
 * 功能特性：
 * 1. 支持基于规则的路由策略
 * 2. 支持读写分离路由
 * 3. 支持分库分表路由
 * 4. 支持自定义路由规则
 * 5. 支持从 Nacos 配置中心动态加载路由规则
 * 6. 支持路由规则热更新
 * 
 * @author lixiangyu
 */
@Slf4j
@Component
public class TrafficRoutingConfig {
    
    @Autowired
    private DynamicConfigManager dynamicConfigManager;
    
    /**
     * 路由规则缓存
     * Key: 规则名称, Value: 路由规则
     */
    private final Map<String, RoutingRule> routingRules = new ConcurrentHashMap<>();
    
    /**
     * 默认路由规则
     */
    private RoutingRule defaultRule;
    
    /**
     * 初始化路由配置
     */
    @PostConstruct
    public void init() {
        loadRoutingConfig();
        registerConfigListener();
        log.info("流量路由配置初始化完成");
    }
    
    /**
     * 获取路由规则
     * 
     * @param ruleName 规则名称
     * @return 路由规则，如果不存在则返回默认规则
     */
    public RoutingRule getRoutingRule(String ruleName) {
        RoutingRule rule = routingRules.get(ruleName);
        return rule != null ? rule : defaultRule;
    }
    
    /**
     * 根据 SQL 类型和表名获取目标数据源
     * 
     * @param sqlType SQL 类型（SELECT/INSERT/UPDATE/DELETE）
     * @param tableName 表名
     * @return 目标数据源名称
     */
    public String getTargetDataSource(String sqlType, String tableName) {
        // 查找匹配的路由规则
        for (RoutingRule rule : routingRules.values()) {
            if (rule.matches(sqlType, tableName)) {
                return rule.getTargetDataSource();
            }
        }
        
        // 使用默认规则
        if (defaultRule != null) {
            return defaultRule.getTargetDataSource();
        }
        
        // 默认返回主数据源
        return "ds0";
    }
    
    /**
     * 从 Nacos 加载路由配置
     */
    private void loadRoutingConfig() {
        String configKey = "traffic.routing.config";
        String configJson = dynamicConfigManager.getString(configKey);
        
        if (configJson == null || configJson.trim().isEmpty()) {
            log.warn("未找到流量路由配置，使用默认配置");
            createDefaultRoutingRule();
            return;
        }
        
        try {
            JSONObject config = JSON.parseObject(configJson);
            
            // 解析路由规则
            parseRoutingRules(config);
            
            // 解析默认规则
            if (config.containsKey("defaultRule")) {
                JSONObject defaultRuleConfig = config.getJSONObject("defaultRule");
                defaultRule = parseRoutingRule(defaultRuleConfig);
            } else {
                createDefaultRoutingRule();
            }
            
            log.info("成功加载流量路由配置，规则数量: {}", routingRules.size());
        } catch (Exception e) {
            log.error("解析流量路由配置失败", e);
            createDefaultRoutingRule();
        }
    }
    
    /**
     * 解析路由规则
     */
    private void parseRoutingRules(JSONObject config) {
        routingRules.clear();
        
        // 解析规则列表
        if (config.containsKey("rules")) {
            List<Object> rules = config.getJSONArray("rules");
            for (Object ruleObj : rules) {
                JSONObject ruleConfig = (JSONObject) ruleObj;
                RoutingRule rule = parseRoutingRule(ruleConfig);
                if (rule != null && rule.getName() != null) {
                    routingRules.put(rule.getName(), rule);
                }
            }
        }
    }
    
    /**
     * 解析单个路由规则
     */
    private RoutingRule parseRoutingRule(JSONObject config) {
        RoutingRule rule = new RoutingRule();
        
        rule.setName(config.getString("name"));
        rule.setTargetDataSource(config.getString("targetDataSource"));
        rule.setPriority(config.getIntValue("priority"));
        
        // 解析匹配条件
        if (config.containsKey("sqlTypes")) {
            List<String> sqlTypes = config.getJSONArray("sqlTypes").toJavaList(String.class);
            rule.setSqlTypes(new HashSet<>(sqlTypes));
        }
        
        if (config.containsKey("tableNames")) {
            List<String> tableNames = config.getJSONArray("tableNames").toJavaList(String.class);
            rule.setTableNames(new HashSet<>(tableNames));
        }
        
        if (config.containsKey("tablePatterns")) {
            List<String> patterns = config.getJSONArray("tablePatterns").toJavaList(String.class);
            rule.setTablePatterns(new HashSet<>(patterns));
        }
        
        return rule;
    }
    
    /**
     * 创建默认路由规则
     */
    private void createDefaultRoutingRule() {
        defaultRule = new RoutingRule();
        defaultRule.setName("default");
        defaultRule.setTargetDataSource("ds0");
        defaultRule.setPriority(0);
        log.info("创建默认路由规则");
    }
    
    /**
     * 注册配置变更监听器
     */
    private void registerConfigListener() {
        dynamicConfigManager.addListener("traffic.routing.config", (key, newValue) -> {
            log.info("流量路由配置已更新，重新加载");
            loadRoutingConfig();
        });
    }
    
    /**
     * 路由规则
     */
    @Data
    public static class RoutingRule {
        /**
         * 规则名称
         */
        private String name;
        
        /**
         * 目标数据源
         */
        private String targetDataSource;
        
        /**
         * 优先级（数字越小优先级越高）
         */
        private int priority;
        
        /**
         * 匹配的 SQL 类型（SELECT/INSERT/UPDATE/DELETE）
         */
        private Set<String> sqlTypes;
        
        /**
         * 匹配的表名列表
         */
        private Set<String> tableNames;
        
        /**
         * 匹配的表名模式（支持通配符）
         */
        private Set<String> tablePatterns;
        
        /**
         * 判断是否匹配
         */
        public boolean matches(String sqlType, String tableName) {
            // 检查 SQL 类型
            if (sqlTypes != null && !sqlTypes.isEmpty()) {
                if (!sqlTypes.contains(sqlType)) {
                    return false;
                }
            }
            
            // 检查表名
            if (tableNames != null && !tableNames.isEmpty()) {
                if (!tableNames.contains(tableName)) {
                    return false;
                }
            }
            
            // 检查表名模式
            if (tablePatterns != null && !tablePatterns.isEmpty()) {
                boolean matched = false;
                for (String pattern : tablePatterns) {
                    if (matchesPattern(tableName, pattern)) {
                        matched = true;
                        break;
                    }
                }
                if (!matched) {
                    return false;
                }
            }
            
            return true;
        }
        
        /**
         * 模式匹配（支持 * 和 ? 通配符）
         */
        private boolean matchesPattern(String text, String pattern) {
            if (pattern == null || pattern.isEmpty()) {
                return false;
            }
            
            // 简单的通配符匹配
            if (pattern.equals("*")) {
                return true;
            }
            
            if (pattern.contains("*")) {
                String regex = pattern.replace("*", ".*").replace("?", ".");
                return text.matches(regex);
            }
            
            return text.equals(pattern);
        }
    }
}

