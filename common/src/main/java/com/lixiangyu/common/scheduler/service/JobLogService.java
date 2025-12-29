package com.lixiangyu.common.scheduler.service;

import com.lixiangyu.dal.entity.job.JobLog;

import java.util.List;

/**
 * 任务执行日志服务接口
 * 
 * @author lixiangyu
 */
public interface JobLogService {
    
    /**
     * 保存执行日志
     *
     * @param jobLog 执行日志
     * @return 保存后的日志
     */
    JobLog save(JobLog jobLog);
    
    /**
     * 更新执行日志
     *
     * @param jobLog 执行日志
     * @return 更新后的日志
     */
    JobLog update(JobLog jobLog);
    
    /**
     * 根据ID查询
     *
     * @param id 日志ID
     * @return 执行日志
     */
    JobLog getById(Long id);
    
    /**
     * 根据任务ID查询
     *
     * @param jobId 任务ID
     * @param limit 限制条数
     * @return 执行日志列表
     */
    List<JobLog> listByJobId(Long jobId, Integer limit);
    
    /**
     * 根据执行ID查询
     *
     * @param executionId 执行ID
     * @return 执行日志
     */
    JobLog getByExecutionId(String executionId);
}

