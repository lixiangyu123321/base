package com.lixiangyu.dal.mapper;

import com.lixiangyu.dal.entity.job.JobLog;
import tk.mybatis.mapper.common.Mapper;
import tk.mybatis.mapper.common.MySqlMapper;

/**
 * 任务执行日志 Mapper 接口
 * 
 * @author lixiangyu
 */
public interface JobLogMapper extends Mapper<JobLog>, MySqlMapper<JobLog> {
}

