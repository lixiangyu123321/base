package com.lixiangyu.common.scheduler.core;

/**
 * 任务接口
 * 所有需要自动注册为定时任务的类都必须实现此接口
 * 
 * @author lixiangyu
 */
public interface Job {
    
    /**
     * 执行任务
     * 
     * @param context 任务执行上下文
     * @throws Exception 执行异常
     */
    void execute(JobContext context) throws Exception;
    
    /**
     * 任务执行上下文
     */
    interface JobContext {
        /**
         * 获取任务ID
         *
         * @return 任务ID
         */
        Long getJobId();
        
        /**
         * 获取任务名称
         *
         * @return 任务名称
         */
        String getJobName();
        
        /**
         * 获取任务分组
         *
         * @return 任务分组
         */
        String getJobGroup();
        
        /**
         * 获取任务参数
         *
         * @return 任务参数（JSON格式）
         */
        String getJobParams();
        
        /**
         * 获取执行ID
         *
         * @return 执行ID
         */
        String getExecutionId();
        
        /**
         * 记录执行日志
         *
         * @param log 日志内容
         */
        void log(String log);
        
        /**
         * 记录错误日志
         *
         * @param error 错误信息
         */
        void error(String error);
    }
}

