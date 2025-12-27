package com.lixiangyu.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程池配置类
 * 用于批量更新操作的并发执行
 *
 * @author lixiangyu
 */
@Configuration
@EnableAsync
public class ThreadPoolConfig {

    /**
     * 批量更新线程池
     * 用于并发执行数据库更新操作
     */
    @Bean("batchUpdateExecutor")
    public Executor batchUpdateExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数
        executor.setCorePoolSize(10);
        // 最大线程数
        executor.setMaxPoolSize(20);
        // 队列容量
        executor.setQueueCapacity(200);
        // 线程名前缀
        executor.setThreadNamePrefix("batch-update-");
        // 拒绝策略：调用者运行策略（当线程池满时，由调用线程执行任务）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 等待所有任务结束后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // 等待时间
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}

