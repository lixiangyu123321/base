package com.lixiangyu.common.scheduler.core;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.lixiangyu.common.config.DynamicConfigManager;
import com.lixiangyu.common.scheduler.entity.JobConfig;
import com.lixiangyu.common.scheduler.service.JobConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * 任务配置变更监听器
 * 监听 Nacos 配置中心的配置变更，实时更新数据库
 * 
 * @author lixiangyu
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobConfigChangeListener {
    
    private final DynamicConfigManager configManager;
    private final JobConfigService jobConfigService;
    private final SchedulerManager schedulerManager;
    
    /**
     * 已注册的监听器映射（Data ID -> 是否已注册）
     */
    private final java.util.Map<String, Boolean> registeredListeners = new java.util.concurrent.ConcurrentHashMap<>();
    
    /**
     * 初始化监听器
     */
    @PostConstruct
    public void init() {
        // 监听所有任务配置的变更
        // 配置 Key 格式：scheduler.job.{jobName}.{jobGroup}.{environment}
        // 注意：这里监听的是配置前缀，实际需要监听具体的 Data ID
        // 由于 Nacos 监听机制，我们需要为每个任务单独注册监听器
        // 这里先注册一个通用的监听器，实际使用时会在任务注册时动态添加
        
        log.info("任务配置变更监听器初始化完成");
    }
    
    /**
     * 注册任务配置监听器
     * 在任务自动注册时调用，为每个任务注册独立的配置监听器
     *
     * @param config 任务配置
     */
    public void registerJobConfigListener(JobConfig config) {
        String dataId = buildNacosDataId(config);
        
        // 检查是否已注册
        if (registeredListeners.containsKey(dataId)) {
            log.debug("任务配置监听器已注册，Data ID: {}", dataId);
            return;
        }
        
        // 注册监听器
        configManager.addListener(dataId, (key, newValue) -> {
            try {
                handleConfigChange(key, newValue);
            } catch (Exception e) {
                log.error("处理配置变更失败，Key: {}", key, e);
            }
        });
        
        registeredListeners.put(dataId, true);
        log.info("注册任务配置监听器成功，Data ID: {}, Job Name: {}", dataId, config.getJobName());
    }
    
    /**
     * 构建 Nacos Data ID
     *
     * @param config 任务配置
     * @return Data ID
     */
    private String buildNacosDataId(JobConfig config) {
        return String.format("scheduler.job.%s.%s.%s.json", 
                config.getJobName(), 
                config.getJobGroup(), 
                config.getEnvironment());
    }
    
    /**
     * 处理配置变更
     *
     * @param key 配置键
     * @param newValue 新配置值（JSON 格式）
     */
    private void handleConfigChange(String key, String newValue) {
        log.info("检测到任务配置变更，Key: {}", key);
        
        try {
            // 解析配置键，获取任务信息
            ConfigKeyInfo keyInfo = parseConfigKey(key);
            if (keyInfo == null) {
                log.warn("无法解析配置键，Key: {}", key);
                return;
            }
            
            // 解析配置内容
            JSONObject configJson = JSON.parseObject(newValue);
            if (configJson == null) {
                log.warn("配置内容为空，Key: {}", key);
                return;
            }
            
            // 从数据库查询现有配置
            JobConfig existingConfig = jobConfigService.getByJobNameAndGroup(
                    keyInfo.getJobName(), 
                    keyInfo.getJobGroup(), 
                    keyInfo.getEnvironment());
            
            if (existingConfig == null) {
                log.warn("数据库中没有找到对应配置，Job Name: {}, Group: {}, Environment: {}，将创建新配置", 
                        keyInfo.getJobName(), keyInfo.getJobGroup(), keyInfo.getEnvironment());
                
                // 如果数据库中没有，从 Nacos 配置创建新配置
                existingConfig = createConfigFromNacos(keyInfo, configJson);
            } else {
                // 更新配置
                updateConfigFromNacos(existingConfig, configJson);
            }
            
            // 如果状态变更，更新调度器
            String newStatus = configJson.getString("status");
            if (newStatus != null) {
                JobConfig.JobStatus status = JobConfig.JobStatus.valueOf(newStatus);
                if (status != existingConfig.getStatus()) {
                    updateScheduler(existingConfig, status);
                }
            }
            
            log.info("任务配置更新成功，Job Name: {}, Group: {}", 
                    keyInfo.getJobName(), keyInfo.getJobGroup());
            
        } catch (Exception e) {
            log.error("处理配置变更异常，Key: {}", key, e);
        }
    }
    
    /**
     * 从 Nacos 配置创建新配置
     *
     * @param keyInfo 配置键信息
     * @param configJson Nacos 配置 JSON
     * @return 任务配置
     */
    private JobConfig createConfigFromNacos(ConfigKeyInfo keyInfo, JSONObject configJson) {
        JobConfig config = JobConfig.builder()
                .jobName(keyInfo.getJobName())
                .jobGroup(keyInfo.getJobGroup())
                .environment(keyInfo.getEnvironment())
                .jobType(JobConfig.JobType.valueOf(configJson.getString("jobType")))
                .jobClass(configJson.getString("jobClass"))
                .cronExpression(configJson.getString("cronExpression"))
                .description(configJson.getString("description"))
                .status(JobConfig.JobStatus.valueOf(configJson.getString("status")))
                .retryCount(configJson.getInteger("retryCount"))
                .retryInterval(configJson.getInteger("retryInterval"))
                .timeout(configJson.getInteger("timeout"))
                .alertEnabled(configJson.getBoolean("alertEnabled"))
                .grayReleaseEnabled(configJson.getBoolean("grayReleaseEnabled"))
                .grayReleasePercent(configJson.getInteger("grayReleasePercent"))
                .version(configJson.getInteger("version"))
                .build();
        
        // 解析任务参数
        if (configJson.containsKey("jobParams")) {
            Object jobParams = configJson.get("jobParams");
            if (jobParams instanceof JSONObject) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> params = (java.util.Map<String, Object>) jobParams;
                config.setJobParams(params);
            }
        }
        
        // 保存到数据库
        return jobConfigService.save(config);
    }
    
    /**
     * 从 Nacos 配置更新数据库配置
     *
     * @param config 现有配置
     * @param configJson Nacos 配置 JSON
     */
    private void updateConfigFromNacos(JobConfig config, JSONObject configJson) {
        // 更新 Cron 表达式
        if (configJson.containsKey("cronExpression")) {
            config.setCronExpression(configJson.getString("cronExpression"));
        }
        
        // 更新任务参数
        if (configJson.containsKey("jobParams")) {
            Object jobParams = configJson.get("jobParams");
            if (jobParams instanceof JSONObject) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> params = (java.util.Map<String, Object>) jobParams;
                config.setJobParams(params);
            }
        }
        
        // 更新描述
        if (configJson.containsKey("description")) {
            config.setDescription(configJson.getString("description"));
        }
        
        // 更新状态
        if (configJson.containsKey("status")) {
            String statusStr = configJson.getString("status");
            if (statusStr != null) {
                config.setStatus(JobConfig.JobStatus.valueOf(statusStr));
            }
        }
        
        // 更新重试配置
        if (configJson.containsKey("retryCount")) {
            config.setRetryCount(configJson.getInteger("retryCount"));
        }
        if (configJson.containsKey("retryInterval")) {
            config.setRetryInterval(configJson.getInteger("retryInterval"));
        }
        
        // 更新超时时间
        if (configJson.containsKey("timeout")) {
            config.setTimeout(configJson.getInteger("timeout"));
        }
        
        // 更新告警配置
        if (configJson.containsKey("alertEnabled")) {
            config.setAlertEnabled(configJson.getBoolean("alertEnabled"));
        }
        
        // 保存到数据库
        jobConfigService.update(config);
    }
    
    /**
     * 更新调度器
     *
     * @param config 任务配置
     * @param newStatus 新状态
     */
    private void updateScheduler(JobConfig config, JobConfig.JobStatus newStatus) {
        try {
            if (newStatus == JobConfig.JobStatus.RUNNING) {
                // 启动任务
                schedulerManager.addJob(config);
            } else if (newStatus == JobConfig.JobStatus.STOPPED) {
                // 停止任务
                schedulerManager.removeJob(config.getId());
            } else if (newStatus == JobConfig.JobStatus.PAUSED) {
                // 暂停任务
                schedulerManager.pauseJob(config.getId());
            }
        } catch (Exception e) {
            log.error("更新调度器失败，Job ID: {}, Status: {}", config.getId(), newStatus, e);
        }
    }
    
    /**
     * 解析配置键
     * 格式：scheduler.job.{jobName}.{jobGroup}.{environment}.json
     *
     * @param key 配置键（Data ID）
     * @return 配置键信息
     */
    private ConfigKeyInfo parseConfigKey(String key) {
        // 格式：scheduler.job.{jobName}.{jobGroup}.{environment}.json
        // 移除 .json 后缀
        String keyWithoutSuffix = key.replace(".json", "");
        String[] parts = keyWithoutSuffix.split("\\.");
        
        if (parts.length >= 5) {
            // scheduler.job.{jobName}.{jobGroup}.{environment}
            String jobName = parts[2];
            String jobGroup = parts[3];
            String environment = parts[4];
            
            return ConfigKeyInfo.builder()
                    .jobName(jobName)
                    .jobGroup(jobGroup)
                    .environment(environment)
                    .build();
        }
        return null;
    }
    
    /**
     * 配置键信息
     */
    @lombok.Data
    @lombok.Builder
    private static class ConfigKeyInfo {
        private String jobName;
        private String jobGroup;
        private String environment;
    }
}

