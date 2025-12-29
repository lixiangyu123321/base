package com.lixiangyu.dal.entity.job;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Table;
import java.util.Date;

/**
 * 任务执行日志实体
 * 
 * @author lixiangyu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "scheduler_job_log")
public class JobLog {
    
    /**
     * 主键ID
     */
    private Long id;
    
    /**
     * 任务ID
     */
    private Long jobId;
    
    /**
     * 任务名称
     */
    private String jobName;
    
    /**
     * 任务分组
     */
    private String jobGroup;
    
    /**
     * 触发器名称
     */
    private String triggerName;
    
    /**
     * 触发器分组
     */
    private String triggerGroup;
    
    /**
     * 执行ID
     */
    private String executionId;
    
    /**
     * 开始时间
     */
    private Date startTime;
    
    /**
     * 结束时间
     */
    private Date endTime;
    
    /**
     * 执行时长（毫秒）
     */
    private Long duration;
    
    /**
     * 执行状态
     */
    private ExecutionStatus status;
    
    /**
     * 重试次数
     */
    private Integer retryCount;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * 执行日志
     */
    private String executionLog;
    
    /**
     * 执行服务器IP
     */
    private String serverIp;
    
    /**
     * 执行服务器名称
     */
    private String serverName;
    
    /**
     * 执行状态枚举
     */
    public enum ExecutionStatus {
        /**
         * 成功
         */
        SUCCESS,
        
        /**
         * 失败
         */
        FAILED,
        
        /**
         * 运行中
         */
        RUNNING
    }
}

