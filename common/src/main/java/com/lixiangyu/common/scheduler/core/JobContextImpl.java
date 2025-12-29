package com.lixiangyu.common.scheduler.core;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * 任务执行上下文实现
 * 
 * @author lixiangyu
 */
@Data
@Builder
public class JobContextImpl implements Job.JobContext {
    
    private Long jobId;
    private String jobName;
    private String jobGroup;
    private String jobParams;
    private String executionId;
    
    /**
     * 日志记录器（由具体实现注入）
     */
    private JobLogger logger;
    
    public JobContextImpl() {
        this.executionId = UUID.randomUUID().toString();
    }
    
    public JobContextImpl(Long jobId, String jobName, String jobGroup, String jobParams, String executionId, JobLogger logger) {
        this.jobId = jobId;
        this.jobName = jobName;
        this.jobGroup = jobGroup;
        this.jobParams = jobParams;
        this.executionId = executionId != null ? executionId : UUID.randomUUID().toString();
        this.logger = logger;
    }
    
    @Override
    public void log(String log) {
        if (logger != null) {
            logger.log(log);
        }
    }
    
    @Override
    public void error(String error) {
        if (logger != null) {
            logger.error(error);
        }
    }
    
    /**
     * 日志记录器接口
     */
    public interface JobLogger {
        void log(String log);
        void error(String error);
    }
}

