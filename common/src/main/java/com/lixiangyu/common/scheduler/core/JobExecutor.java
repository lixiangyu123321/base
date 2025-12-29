package com.lixiangyu.common.scheduler.core;

import com.lixiangyu.common.scheduler.entity.JobConfig;
import com.lixiangyu.common.scheduler.entity.JobLog;
import com.lixiangyu.common.scheduler.service.JobLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;

/**
 * 任务执行器
 * 统一的任务执行入口，处理任务执行、日志记录、重试等
 * 
 * @author lixiangyu
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobExecutor {
    
    private final ApplicationContext applicationContext;
    private final JobLogService jobLogService;
    
    /**
     * 执行任务
     *
     * @param config 任务配置
     * @return 执行结果
     */
    public ExecutionResult execute(JobConfig config) {
        String executionId = UUID.randomUUID().toString();
        Date startTime = new Date();
        
        // 创建执行日志
        JobLog jobLog = JobLog.builder()
                .jobId(config.getId())
                .jobName(config.getJobName())
                .jobGroup(config.getJobGroup())
                .executionId(executionId)
                .startTime(startTime)
                .status(JobLog.ExecutionStatus.RUNNING)
                .retryCount(0)
                .serverIp(getServerIp())
                .serverName(getServerName())
                .build();
        
        jobLogService.save(jobLog);
        
        // 创建任务上下文
        JobContextImpl context = JobContextImpl.builder()
                .jobId(config.getId())
                .jobName(config.getJobName())
                .jobGroup(config.getJobGroup())
                .jobParams(convertParamsToString(config.getJobParams()))
                .executionId(executionId)
                .logger(new JobContextImpl.JobLogger() {
                    @Override
                    public void log(String log) {
                        appendLog(jobLog, log);
                    }
                    
                    @Override
                    public void error(String error) {
                        appendError(jobLog, error);
                    }
                })
                .build();
        
        // 执行任务（带重试）
        ExecutionResult result = executeWithRetry(config, context, jobLog);
        
        // 更新执行日志
        Date endTime = new Date();
        jobLog.setEndTime(endTime);
        jobLog.setDuration(endTime.getTime() - startTime.getTime());
        jobLog.setStatus(result.isSuccess() 
                ? JobLog.ExecutionStatus.SUCCESS 
                : JobLog.ExecutionStatus.FAILED);
        jobLog.setErrorMessage(result.getErrorMessage());
        
        jobLogService.update(jobLog);
        
        return result;
    }
    
    /**
     * 带重试的任务执行
     *
     * @param config 任务配置
     * @param context 任务上下文
     * @param jobLog 执行日志
     * @return 执行结果
     */
    private ExecutionResult executeWithRetry(JobConfig config, JobContextImpl context, JobLog jobLog) {
        int maxRetries = config.getRetryCount() != null ? config.getRetryCount() : 3;
        int retryInterval = config.getRetryInterval() != null ? config.getRetryInterval() : 60;
        
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                // 获取任务实例
                Job job = getJobInstance(config);
                
                if (job == null) {
                    throw new RuntimeException("无法获取任务实例: " + config.getJobClass());
                }
                
                // 执行任务
                job.execute(context);
                
                // 执行成功
                return ExecutionResult.success();
                
            } catch (Exception e) {
                log.error("任务执行失败，Job ID: {}, Name: {}, 尝试次数: {}/{}", 
                        config.getId(), config.getJobName(), attempt + 1, maxRetries + 1, e);
                
                context.error("执行失败: " + e.getMessage());
                jobLog.setRetryCount(attempt);
                
                // 如果是最后一次尝试，返回失败
                if (attempt >= maxRetries) {
                    return ExecutionResult.failed(e.getMessage());
                }
                
                // 等待重试间隔
                try {
                    Thread.sleep(retryInterval * 1000L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return ExecutionResult.failed("任务执行被中断");
                }
            }
        }
        
        return ExecutionResult.failed("任务执行失败，已达到最大重试次数");
    }
    
    /**
     * 获取任务实例
     *
     * @param config 任务配置
     * @return 任务实例
     */
    private Job getJobInstance(JobConfig config) {
        try {
            // 首先尝试从 Spring 容器获取
            java.util.Map<String, Job> jobBeans = applicationContext.getBeansOfType(Job.class);
            for (Job job : jobBeans.values()) {
                if (job.getClass().getName().equals(config.getJobClass())) {
                    return job;
                }
            }
            
            // 如果容器中没有，尝试反射创建（不推荐，但作为降级方案）
            Class<?> jobClass = Class.forName(config.getJobClass());
            if (Job.class.isAssignableFrom(jobClass)) {
                @SuppressWarnings("deprecation")
                Job job = (Job) jobClass.newInstance();
                return job;
            }
            
        } catch (Exception e) {
            log.error("获取任务实例失败，Job Class: {}", config.getJobClass(), e);
        }
        
        return null;
    }
    
    /**
     * 追加日志
     *
     * @param jobLog 执行日志
     * @param log 日志内容
     */
    private void appendLog(JobLog jobLog, String log) {
        String existingLog = jobLog.getExecutionLog();
        String newLog = existingLog != null 
                ? existingLog + "\n" + new Date() + " - " + log
                : new Date() + " - " + log;
        jobLog.setExecutionLog(newLog);
    }
    
    /**
     * 追加错误日志
     *
     * @param jobLog 执行日志
     * @param error 错误信息
     */
    private void appendError(JobLog jobLog, String error) {
        appendLog(jobLog, "[ERROR] " + error);
        String existingError = jobLog.getErrorMessage();
        String newError = existingError != null 
                ? existingError + "\n" + error
                : error;
        jobLog.setErrorMessage(newError);
    }
    
    /**
     * 转换参数为字符串
     *
     * @param params 参数Map
     * @return JSON字符串
     */
    private String convertParamsToString(java.util.Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return null;
        }
        return com.alibaba.fastjson.JSON.toJSONString(params);
    }
    
    /**
     * 获取服务器IP
     *
     * @return 服务器IP
     */
    private String getServerIp() {
        try {
            java.net.InetAddress addr = java.net.InetAddress.getLocalHost();
            return addr.getHostAddress();
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    /**
     * 获取服务器名称
     *
     * @return 服务器名称
     */
    private String getServerName() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    /**
     * 执行结果
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ExecutionResult {
        private boolean success;
        private String errorMessage;
        
        public static ExecutionResult success() {
            return new ExecutionResult(true, null);
        }
        
        public static ExecutionResult failed(String errorMessage) {
            return new ExecutionResult(false, errorMessage);
        }
    }
}

