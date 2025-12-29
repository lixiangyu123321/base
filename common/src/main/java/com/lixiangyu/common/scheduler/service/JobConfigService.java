package com.lixiangyu.common.scheduler.service;

import com.lixiangyu.common.scheduler.entity.JobConfig;

import java.util.List;

/**
 * 任务配置服务接口
 * 
 * @author lixiangyu
 */
public interface JobConfigService {
    
    /**
     * 保存任务配置
     *
     * @param config 任务配置
     * @return 保存后的配置
     */
    JobConfig save(JobConfig config);
    
    /**
     * 更新任务配置
     *
     * @param config 任务配置
     * @return 更新后的配置
     */
    JobConfig update(JobConfig config);
    
    /**
     * 根据ID查询
     *
     * @param id 配置ID
     * @return 任务配置
     */
    JobConfig getById(Long id);
    
    /**
     * 根据任务名称和分组查询
     *
     * @param jobName 任务名称
     * @param jobGroup 任务分组
     * @param environment 环境
     * @return 任务配置
     */
    JobConfig getByJobNameAndGroup(String jobName, String jobGroup, String environment);
    
    /**
     * 查询所有任务配置
     *
     * @param environment 环境（可选）
     * @return 任务配置列表
     */
    List<JobConfig> listAll(String environment);
    
    /**
     * 根据状态查询
     *
     * @param status 状态
     * @param environment 环境（可选）
     * @return 任务配置列表
     */
    List<JobConfig> listByStatus(JobConfig.JobStatus status, String environment);
    
    /**
     * 删除任务配置
     *
     * @param id 配置ID
     */
    void delete(Long id);
    
    /**
     * 启用任务
     *
     * @param id 配置ID
     */
    void enable(Long id);
    
    /**
     * 禁用任务
     *
     * @param id 配置ID
     */
    void disable(Long id);
    
    /**
     * 暂停任务
     *
     * @param id 配置ID
     */
    void pause(Long id);
    
    /**
     * 恢复任务
     *
     * @param id 配置ID
     */
    void resume(Long id);
}

