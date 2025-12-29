package com.lixiangyu.common.scheduler.service.impl;

import com.lixiangyu.common.scheduler.entity.JobLog;
import com.lixiangyu.common.scheduler.mapper.JobLogMapper;
import com.lixiangyu.common.scheduler.service.JobLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tk.mybatis.mapper.entity.Example;

import java.util.List;

/**
 * 任务执行日志服务实现
 * 
 * @author lixiangyu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobLogServiceImpl implements JobLogService {
    
    private final JobLogMapper jobLogMapper;
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobLog save(JobLog jobLog) {
        jobLogMapper.insertSelective(jobLog);
        return jobLog;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobLog update(JobLog jobLog) {
        jobLogMapper.updateByPrimaryKeySelective(jobLog);
        return jobLog;
    }
    
    @Override
    public JobLog getById(Long id) {
        return jobLogMapper.selectByPrimaryKey(id);
    }
    
    @Override
    public List<JobLog> listByJobId(Long jobId, Integer limit) {
        Example example = new Example(JobLog.class);
        example.createCriteria().andEqualTo("jobId", jobId);
        example.setOrderByClause("start_time DESC");
        
        List<JobLog> logs = jobLogMapper.selectByExample(example);
        
        // 如果指定了限制，截取结果
        if (limit != null && limit > 0 && logs.size() > limit) {
            return logs.subList(0, limit);
        }
        
        return logs;
    }
    
    @Override
    public JobLog getByExecutionId(String executionId) {
        Example example = new Example(JobLog.class);
        example.createCriteria().andEqualTo("executionId", executionId);
        List<JobLog> logs = jobLogMapper.selectByExample(example);
        return logs.isEmpty() ? null : logs.get(0);
    }
}

