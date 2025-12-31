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
 * 分库分表规则管理器
 * 管理分库分表规则，支持从 Nacos 配置中心动态加载和更新
 * 
 * 功能特性：
 * 1. 支持分库规则管理
 * 2. 支持分表规则管理
 * 3. 支持分片算法配置
 * 4. 支持从 Nacos 配置中心动态加载
 * 5. 支持规则热更新
 * 
 * @author lixiangyu
 */
@Slf4j
@Component
public class ShardingRuleManager {
    
    @Autowired
    private DynamicConfigManager dynamicConfigManager;
    
    /**
     * 分库规则映射
     * Key: 逻辑库名, Value: 分库规则
     */
    private final Map<String, DatabaseShardingRule> databaseRules = new ConcurrentHashMap<>();
    
    /**
     * 分表规则映射
     * Key: 逻辑表名, Value: 分表规则
     */
    private final Map<String, TableShardingRule> tableRules = new ConcurrentHashMap<>();
    
    /**
     * 分片算法映射
     * Key: 算法名称, Value: 算法配置
     */
    private final Map<String, ShardingAlgorithmConfig> algorithms = new ConcurrentHashMap<>();
    
    /**
     * 初始化规则管理器
     */
    @PostConstruct
    public void init() {
        loadShardingRules();
        registerConfigListener();
        log.info("分库分表规则管理器初始化完成");
    }
    
    /**
     * 获取分库规则
     */
    public DatabaseShardingRule getDatabaseRule(String databaseName) {
        return databaseRules.get(databaseName);
    }
    
    /**
     * 获取分表规则
     */
    public TableShardingRule getTableRule(String tableName) {
        return tableRules.get(tableName);
    }
    
    /**
     * 获取分片算法配置
     */
    public ShardingAlgorithmConfig getAlgorithm(String algorithmName) {
        return algorithms.get(algorithmName);
    }
    
    /**
     * 从 Nacos 加载分库分表规则
     */
    private void loadShardingRules() {
        String configKey = "sharding.rules";
        String configJson = dynamicConfigManager.getString(configKey);
        
        if (configJson == null || configJson.trim().isEmpty()) {
            log.warn("未找到分库分表规则配置");
            return;
        }
        
        try {
            JSONObject config = JSON.parseObject(configJson);
            
            // 解析分库规则
            if (config.containsKey("databases")) {
                JSONObject databases = config.getJSONObject("databases");
                for (String dbName : databases.keySet()) {
                    JSONObject dbConfig = databases.getJSONObject(dbName);
                    DatabaseShardingRule rule = parseDatabaseRule(dbName, dbConfig);
                    databaseRules.put(dbName, rule);
                }
            }
            
            // 解析分表规则
            if (config.containsKey("tables")) {
                JSONObject tables = config.getJSONObject("tables");
                for (String tableName : tables.keySet()) {
                    JSONObject tableConfig = tables.getJSONObject(tableName);
                    TableShardingRule rule = parseTableRule(tableName, tableConfig);
                    tableRules.put(tableName, rule);
                }
            }
            
            // 解析分片算法
            if (config.containsKey("algorithms")) {
                JSONObject algorithmsConfig = config.getJSONObject("algorithms");
                for (String algName : algorithmsConfig.keySet()) {
                    JSONObject algConfig = algorithmsConfig.getJSONObject(algName);
                    ShardingAlgorithmConfig algorithm = parseAlgorithm(algName, algConfig);
                    algorithms.put(algName, algorithm);
                }
            }
            
            log.info("成功加载分库分表规则，分库规则: {}, 分表规则: {}, 算法: {}", 
                databaseRules.size(), tableRules.size(), algorithms.size());
        } catch (Exception e) {
            log.error("解析分库分表规则失败", e);
        }
    }
    
    /**
     * 解析分库规则
     */
    private DatabaseShardingRule parseDatabaseRule(String databaseName, JSONObject config) {
        DatabaseShardingRule rule = new DatabaseShardingRule();
        rule.setLogicDatabase(databaseName);
        rule.setShardingColumn(config.getString("shardingColumn"));
        rule.setShardingAlgorithmName(config.getString("shardingAlgorithmName"));
        rule.setActualDataNodes(config.getString("actualDataNodes"));
        return rule;
    }
    
    /**
     * 解析分表规则
     */
    private TableShardingRule parseTableRule(String tableName, JSONObject config) {
        TableShardingRule rule = new TableShardingRule();
        rule.setLogicTable(tableName);
        rule.setShardingColumn(config.getString("shardingColumn"));
        rule.setShardingAlgorithmName(config.getString("shardingAlgorithmName"));
        rule.setActualDataNodes(config.getString("actualDataNodes"));
        return rule;
    }
    
    /**
     * 解析分片算法
     */
    private ShardingAlgorithmConfig parseAlgorithm(String algorithmName, JSONObject config) {
        ShardingAlgorithmConfig algorithm = new ShardingAlgorithmConfig();
        algorithm.setName(algorithmName);
        algorithm.setType(config.getString("type"));
        algorithm.setProperties(config.getJSONObject("properties"));
        return algorithm;
    }
    
    /**
     * 注册配置变更监听器
     */
    private void registerConfigListener() {
        dynamicConfigManager.addListener("sharding.rules", (key, newValue) -> {
            log.info("分库分表规则已更新，重新加载");
            loadShardingRules();
        });
    }
    
    /**
     * 分库规则
     */
    @Data
    public static class DatabaseShardingRule {
        private String logicDatabase;
        private String shardingColumn;
        private String shardingAlgorithmName;
        private String actualDataNodes;
    }
    
    /**
     * 分表规则
     */
    @Data
    public static class TableShardingRule {
        private String logicTable;
        private String shardingColumn;
        private String shardingAlgorithmName;
        private String actualDataNodes;
    }
    
    /**
     * 分片算法配置
     */
    @Data
    public static class ShardingAlgorithmConfig {
        private String name;
        private String type;
        private JSONObject properties;
    }
}

