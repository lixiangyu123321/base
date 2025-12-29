package com.lixiangyu.common.scheduler.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 任务配置实体
 * 
 * @author lixiangyu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobConfig {
    
    /**
     * 主键ID
     */
    private Long id;
    
    /**
     * 任务名称
     */
    private String jobName;
    
    /**
     * 任务分组
     */
    private String jobGroup;
    
    /**
     * 任务类型
     */
    private JobType jobType;
    
    /**
     * 任务执行类
     */
    private String jobClass;
    
    /**
     * Cron表达式
     */
    private String cronExpression;
    
    /**
     * 任务参数（JSON格式）
     */
    private Map<String, Object> jobParams;
    
    /**
     * 任务描述
     */
    private String description;
    
    /**
     * 任务状态
     */
    private JobStatus status;
    
    /**
     * 环境
     */
    private String environment;
    
    /**
     * 重试次数
     */
    private Integer retryCount;
    
    /**
     * 重试间隔（秒）
     */
    private Integer retryInterval;
    
    /**
     * 超时时间（秒）
     */
    private Integer timeout;
    
    /**
     * 是否启用告警
     */
    private Boolean alertEnabled;
    
    /**
     * 告警类型列表
     */
    private List<AlertType> alertTypes;
    
    /**
     * 告警接收人
     */
    private AlertReceivers alertReceivers;
    
    /**
     * 是否启用灰度发布
     */
    private Boolean grayReleaseEnabled;
    
    /**
     * 灰度发布百分比（0-100）
     */
    private Integer grayReleasePercent;
    
    /**
     * 版本号
     */
    private Integer version;
    
    /**
     * 创建人
     */
    private String creator;
    
    /**
     * 修改人
     */
    private String modifier;
    
    /**
     * 创建时间
     */
    private Date createTime;
    
    /**
     * 更新时间
     */
    private Date updateTime;
    
    /**
     * 任务类型枚举
     */
    public enum JobType {
        /**
         * Quartz
         */
        QUARTZ,
        
        /**
         * XXL-Job
         */
        XXL_JOB
    }
    
    /**
     * 任务状态枚举
     */
    public enum JobStatus {
        /**
         * 运行中
         */
        RUNNING,
        
        /**
         * 已停止
         */
        STOPPED,
        
        /**
         * 已暂停
         */
        PAUSED
    }
    
    /**
     * 告警类型枚举
     */
    public enum AlertType {
        /**
         * 钉钉
         */
        DINGTALK,
        
        /**
         * 企业微信
         */
        WECHAT,
        
        /**
         * 邮件
         */
        EMAIL
    }
    
    /**
     * 告警接收人
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlertReceivers {
        /**
         * 钉钉接收人（手机号或用户ID）
         */
        private List<String> dingtalk;
        
        /**
         * 企业微信接收人（用户ID）
         */
        private List<String> wechat;
        
        /**
         * 邮件接收人（邮箱地址）
         */
        private List<String> email;
    }
}

