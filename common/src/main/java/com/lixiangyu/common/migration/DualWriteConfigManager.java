package com.lixiangyu.common.migration;

import com.lixiangyu.common.config.DynamicConfigManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 双写配置管理器
 * 基于配置中心动态控制双写功能
 * 
 * 功能特性：
 * 1. 从配置中心读取双写开关
 * 2. 支持按表动态开启/关闭双写
 * 3. 支持写源库/写目标库的独立控制
 * 4. 配置变更自动生效
 *
 * 12-31 18:56 这里就是从配置中心获得配置
 * @author lixiangyu
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DualWriteConfigManager {
    
    private final DynamicConfigManager configManager;
    
    /**
     * 双写开关（全局）
     */
    private volatile boolean dualWriteEnabled = false;
    
    /**
     * 写源库开关（全局）
     */
    private volatile boolean writeSourceEnabled = true;
    
    /**
     * 写目标库开关（全局）
     */
    private volatile boolean writeTargetEnabled = false;
    
    /**
     * 表级别的双写开关（表名 -> 是否启用）
     */
    private final Map<String, Boolean> tableDualWriteMap = new ConcurrentHashMap<>();
    
    /**
     * 表级别的写源库开关
     */
    private final Map<String, Boolean> tableWriteSourceMap = new ConcurrentHashMap<>();
    
    /**
     * 表级别的写目标库开关
     */
    private final Map<String, Boolean> tableWriteTargetMap = new ConcurrentHashMap<>();
    
    /**
     * 初始化配置
     */
    @PostConstruct
    public void init() {
        loadConfig();
        
        // 监听配置变更
        configManager.addListener("dual.write.enabled", (key, newValue) -> {
            dualWriteEnabled = Boolean.parseBoolean(newValue);
            log.info("双写开关变更: {}", dualWriteEnabled);
        });
        
        configManager.addListener("dual.write.writeSource.enabled", (key, newValue) -> {
            writeSourceEnabled = Boolean.parseBoolean(newValue);
            log.info("写源库开关变更: {}", writeSourceEnabled);
        });
        
        configManager.addListener("dual.write.writeTarget.enabled", (key, newValue) -> {
            writeTargetEnabled = Boolean.parseBoolean(newValue);
            log.info("写目标库开关变更: {}", writeTargetEnabled);
        });
        
        // 监听表级别的配置变更
        configManager.addListener("dual.write.tables", (key, newValue) -> {
            loadTableConfig(newValue);
        });
    }
    
    /**
     * 加载配置
     */
    private void loadConfig() {
        // 全局开关
        dualWriteEnabled = Boolean.parseBoolean(
                configManager.getString("dual.write.enabled", "false"));
        writeSourceEnabled = Boolean.parseBoolean(
                configManager.getString("dual.write.writeSource.enabled", "true"));
        writeTargetEnabled = Boolean.parseBoolean(
                configManager.getString("dual.write.writeTarget.enabled", "false"));
        
        // 表级别配置
        String tablesConfig = configManager.getString("dual.write.tables", "");
        loadTableConfig(tablesConfig);
        
        log.info("双写配置加载完成 - 全局开关: {}, 写源库: {}, 写目标库: {}", 
                dualWriteEnabled, writeSourceEnabled, writeTargetEnabled);
    }
    
    /**
     * 加载表级别配置
     * 配置格式：table1:true:true,table2:true:false
     * 格式：表名:写源库:写目标库
     */
    private void loadTableConfig(String tablesConfig) {
        if (tablesConfig == null || tablesConfig.trim().isEmpty()) {
            return;
        }
        
        tableDualWriteMap.clear();
        tableWriteSourceMap.clear();
        tableWriteTargetMap.clear();
        
        String[] tableConfigs = tablesConfig.split(",");
        for (String tableConfig : tableConfigs) {
            String[] parts = tableConfig.trim().split(":");
            if (parts.length >= 3) {
                String tableName = parts[0].trim();
                boolean writeSource = Boolean.parseBoolean(parts[1].trim());
                boolean writeTarget = Boolean.parseBoolean(parts[2].trim());
                
                tableWriteSourceMap.put(tableName, writeSource);
                tableWriteTargetMap.put(tableName, writeTarget);
                tableDualWriteMap.put(tableName, writeSource || writeTarget);
                
                log.debug("表 {} 双写配置: 写源库={}, 写目标库={}", tableName, writeSource, writeTarget);
            }
        }
    }
    
    /**
     * 检查是否应该双写
     *
     * @param tableName 表名
     * @return 是否应该双写
     */
    public boolean shouldDualWrite(String tableName) {
        // 1. 检查表级别配置
        Boolean tableDualWrite = tableDualWriteMap.get(tableName);
        if (tableDualWrite != null) {
            return tableDualWrite;
        }
        
        // 2. 检查全局配置
        return dualWriteEnabled;
    }
    
    /**
     * 检查是否应该写源库
     *
     * @param tableName 表名
     * @return 是否应该写源库
     */
    public boolean shouldWriteSource(String tableName) {
        // 1. 检查表级别配置
        Boolean tableWriteSource = tableWriteSourceMap.get(tableName);
        if (tableWriteSource != null) {
            return tableWriteSource;
        }
        
        // 2. 检查全局配置
        return writeSourceEnabled;
    }
    
    /**
     * 检查是否应该写目标库
     *
     * @param tableName 表Name
     * @return 是否应该写目标库
     */
    public boolean shouldWriteTarget(String tableName) {
        // 1. 检查表级别配置
        Boolean tableWriteTarget = tableWriteTargetMap.get(tableName);
        if (tableWriteTarget != null) {
            return tableWriteTarget;
        }
        
        // 2. 检查全局配置
        return writeTargetEnabled;
    }
    
    /**
     * 获取所有启用双写的表
     *
     * @return 表名集合
     */
    public Set<String> getDualWriteTables() {
        Set<String> tables = new HashSet<>();
        for (Map.Entry<String, Boolean> entry : tableDualWriteMap.entrySet()) {
            if (entry.getValue()) {
                tables.add(entry.getKey());
            }
        }
        return tables;
    }
}

