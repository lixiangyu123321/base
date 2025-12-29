package com.lixiangyu.common.scheduler.annotation;

import com.lixiangyu.common.scheduler.entity.JobConfig;

import java.lang.annotation.*;

/**
 * 定时任务注解
 * 用于标记需要自动注册的定时任务
 * 
 * 支持两种配置方式：
 * 1. 注解配置：直接在注解中配置调度信息
 * 2. 数据库配置：通过 jobName 和 jobGroup 从数据库加载配置
 * 
 * @author lixiangyu
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ScheduledJob {
    
    /**
     * 任务名称（必填）
     * 如果配置了 jobName，会优先从数据库加载配置
     *
     * @return 任务名称
     */
    String jobName();
    
    /**
     * 任务分组（可选，默认：DEFAULT）
     *
     * @return 任务分组
     */
    String jobGroup() default "DEFAULT";
    
    /**
     * 任务类型（可选，默认：QUARTZ）
     * 如果数据库中有配置，优先使用数据库配置
     *
     * @return 任务类型
     */
    JobConfig.JobType jobType() default JobConfig.JobType.QUARTZ;
    
    /**
     * Cron 表达式（可选）
     * 如果数据库中有配置，优先使用数据库配置
     *
     * @return Cron 表达式
     */
    String cronExpression() default "";
    
    /**
     * 任务描述（可选）
     *
     * @return 任务描述
     */
    String description() default "";
    
    /**
     * 是否自动启动（可选，默认：true）
     * true：应用启动时自动注册并启动
     * false：仅注册，不启动
     *
     * @return 是否自动启动
     */
    boolean autoStart() default true;
    
    /**
     * 是否从数据库加载配置（可选，默认：true）
     * true：优先从数据库加载配置，如果数据库中没有，使用注解配置
     * false：仅使用注解配置
     *
     * @return 是否从数据库加载配置
     */
    boolean loadFromDatabase() default true;
    
    /**
     * 环境（可选，默认：从配置读取）
     * 用于区分不同环境的配置
     *
     * @return 环境
     */
    String environment() default "";
}

