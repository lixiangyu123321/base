package com.lixiangyu.common.scheduler.service.impl;

import com.lixiangyu.common.scheduler.entity.JobConfig;
import com.lixiangyu.common.scheduler.mapper.JobConfigMapper;
import com.lixiangyu.common.scheduler.service.JobConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 任务配置服务实现
 * 
 * @author lixiangyu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobConfigServiceImpl implements JobConfigService {
    
    private final JobConfigMapper jobConfigMapper;
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobConfig save(JobConfig config) {
        if (config.getId() == null) {
            // 新增
            config.setCreateTime(new Date());
            config.setUpdateTime(new Date());
            config.setVersion(1);
            if (config.getStatus() == null) {
                config.setStatus(JobConfig.JobStatus.STOPPED);
            }
            jobConfigMapper.insertSelective(config);
            log.info("保存任务配置成功，Job Name: {}, Group: {}", config.getJobName(), config.getJobGroup());
        } else {
            // 更新
            return update(config);
        }
        return config;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobConfig update(JobConfig config) {
        config.setUpdateTime(new Date());
        if (config.getVersion() != null) {
            config.setVersion(config.getVersion() + 1);
        } else {
            JobConfig existing = jobConfigMapper.selectByPrimaryKey(config.getId());
            if (existing != null) {
                config.setVersion(existing.getVersion() + 1);
            }
        }
        jobConfigMapper.updateByPrimaryKeySelective(config);
        log.info("更新任务配置成功，Job ID: {}, Name: {}", config.getId(), config.getJobName());
        return config;
    }
    
    @Override
    public JobConfig getById(Long id) {
        return jobConfigMapper.selectByPrimaryKey(id);
    }
    
    @Override
    public JobConfig getByJobNameAndGroup(String jobName, String jobGroup, String environment) {
        return jobConfigMapper.selectByJobNameAndGroup(jobName, jobGroup, environment);
    }
    
    @Override
    public List<JobConfig> listAll(String environment) {
        return jobConfigMapper.selectAllWithEnvironment(environment);
    }
    
    @Override
    public List<JobConfig> listByStatus(JobConfig.JobStatus status, String environment) {
        return jobConfigMapper.selectByStatus(status.name(), environment);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        jobConfigMapper.deleteByPrimaryKey(id);
        log.info("删除任务配置成功，Job ID: {}", id);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enable(Long id) {
        JobConfig config = getById(id);
        if (config != null) {
            config.setStatus(JobConfig.JobStatus.RUNNING);
            config.setUpdateTime(new Date());
            jobConfigMapper.updateByPrimaryKeySelective(config);
            log.info("启用任务成功，Job ID: {}", id);
        }
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disable(Long id) {
        JobConfig config = getById(id);
        if (config != null) {
            config.setStatus(JobConfig.JobStatus.STOPPED);
            config.setUpdateTime(new Date());
            jobConfigMapper.updateByPrimaryKeySelective(config);
            log.info("禁用任务成功，Job ID: {}", id);
        }
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pause(Long id) {
        JobConfig config = getById(id);
        if (config != null) {
            config.setStatus(JobConfig.JobStatus.PAUSED);
            config.setUpdateTime(new Date());
            jobConfigMapper.updateByPrimaryKeySelective(config);
            log.info("暂停任务成功，Job ID: {}", id);
        }
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resume(Long id) {
        JobConfig config = getById(id);
        if (config != null) {
            config.setStatus(JobConfig.JobStatus.RUNNING);
            config.setUpdateTime(new Date());
            jobConfigMapper.updateByPrimaryKeySelective(config);
            log.info("恢复任务成功，Job ID: {}", id);
        }
    }
}

