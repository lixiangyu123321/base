package com.lixiangyu.common.scheduler.mapper;

import com.lixiangyu.common.scheduler.entity.JobConfig;
import org.apache.ibatis.annotations.Param;
import tk.mybatis.mapper.common.Mapper;
import tk.mybatis.mapper.common.MySqlMapper;

import java.util.List;

/**
 * 任务配置 Mapper 接口
 * 
 * @author lixiangyu
 */
public interface JobConfigMapper extends Mapper<JobConfig>, MySqlMapper<JobConfig> {
    
    /**
     * 根据任务名称、分组和环境查询
     *
     * @param jobName 任务名称
     * @param jobGroup 任务分组
     * @param environment 环境
     * @return 任务配置
     */
    JobConfig selectByJobNameAndGroup(@Param("jobName") String jobName,
                                      @Param("jobGroup") String jobGroup,
                                      @Param("environment") String environment);
    
    /**
     * 根据状态查询
     *
     * @param status 状态
     * @param environment 环境（可选）
     * @return 任务配置列表
     */
    List<JobConfig> selectByStatus(@Param("status") String status,
                                   @Param("environment") String environment);
    
    /**
     * 查询所有（可选环境过滤）
     *
     * @param environment 环境（可选）
     * @return 任务配置列表
     */
    List<JobConfig> selectAllWithEnvironment(@Param("environment") String environment);
}

