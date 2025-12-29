package com.lixiangyu.common.scheduler.example;

import com.lixiangyu.common.scheduler.annotation.ScheduledJob;
import com.lixiangyu.common.scheduler.core.Job;
import com.lixiangyu.common.scheduler.entity.JobConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 示例任务
 * 演示如何使用 @ScheduledJob 注解自动注册任务
 * 
 * @author lixiangyu
 */
@Slf4j
@Component
@ScheduledJob(
        jobName = "exampleJob",
        jobGroup = "EXAMPLE",
        jobType = JobConfig.JobType.QUARTZ,
        cronExpression = "0 0/5 * * * ?",  // 每5分钟执行一次
        description = "示例任务：演示自动注册功能",
        autoStart = true,
        loadFromDatabase = true
)
public class ExampleJob implements Job {
    
    @Override
    public void execute(JobContext context) throws Exception {
        context.log("开始执行示例任务");
        
        try {
            // 业务逻辑
            String jobName = context.getJobName();
            String jobParams = context.getJobParams();
            
            context.log("任务名称: " + jobName);
            context.log("任务参数: " + jobParams);
            
            // 模拟业务处理
            Thread.sleep(1000);
            
            context.log("示例任务执行完成");
            
        } catch (Exception e) {
            context.error("示例任务执行失败: " + e.getMessage());
            throw e;
        }
    }
}

