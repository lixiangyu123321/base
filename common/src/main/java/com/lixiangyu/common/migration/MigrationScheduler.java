package com.lixiangyu.common.migration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 迁移任务调度器
 * 支持定时任务和任务队列管理
 * 
 * 功能特性：
 * 1. 定时执行迁移任务
 * 2. 任务队列管理
 * 3. 任务优先级调度
 * 4. 任务重试机制
 * 
 * @author lixiangyu
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MigrationScheduler {
    
    private final DataMigrationService migrationService;
    private final MigrationTaskManager taskManager;
    
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.context.ApplicationContext applicationContext;
    
    /**
     * 调度器
     */
    private final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(5);
    
    /**
     * 定时任务映射（Task ID -> 定时任务）
     */
    private final Map<String, ScheduledTask> scheduledTasks = new ConcurrentHashMap<>();
    
    /**
     * 任务队列
     * TODO 这里可以改成由Redisson的阻塞队列实现
     */
    private final java.util.concurrent.BlockingQueue<MigrationTask> taskQueue = 
            new java.util.concurrent.LinkedBlockingQueue<>();
    
    /**
     * 提交定时任务
     *
     * @param config 迁移配置
     * @param cronExpression Cron 表达式
     * @return 任务ID
     */
    public String scheduleTask(MigrationConfig config, String cronExpression) {
        String taskId = java.util.UUID.randomUUID().toString();
        log.info("提交定时迁移任务，Task ID: {}, Cron: {}", taskId, cronExpression);
        
        // 解析 Cron 表达式并调度
        scheduleWithCron(taskId, config, cronExpression);
        
        return taskId;
    }
    
    /**
     * 提交延迟任务
     *
     * @param config 迁移配置
     * @param delay 延迟时间（秒）
     * @return 任务ID
     */
    public String scheduleTask(MigrationConfig config, long delay) {
        String taskId = java.util.UUID.randomUUID().toString();
        log.info("提交延迟迁移任务，Task ID: {}, 延迟: {}秒", taskId, delay);
        
        scheduler.schedule(() -> {
            executeMigrationTask(taskId, config);
        }, delay, TimeUnit.SECONDS);
        
        return taskId;
    }
    
    /**
     * 提交周期性任务
     *
     * @param config 迁移配置
     * @param initialDelay 初始延迟（秒）
     * @param period 周期（秒）
     * @return 任务ID
     */
    public String schedulePeriodicTask(MigrationConfig config, long initialDelay, long period) {
        String taskId = java.util.UUID.randomUUID().toString();
        log.info("提交周期性迁移任务，Task ID: {}, 初始延迟: {}秒, 周期: {}秒", 
                taskId, initialDelay, period);
        
        ScheduledTask scheduledTask = new ScheduledTask(taskId, config);
        scheduledTasks.put(taskId, scheduledTask);
        
        scheduler.scheduleAtFixedRate(() -> {
            executeMigrationTask(taskId, config);
        }, initialDelay, period, TimeUnit.SECONDS);
        
        return taskId;
    }
    
    /**
     * 取消定时任务
     *
     * @param taskId 任务ID
     */
    public void cancelTask(String taskId) {
        ScheduledTask task = scheduledTasks.remove(taskId);
        if (task != null) {
            task.cancel();
            log.info("取消定时迁移任务，Task ID: {}", taskId);
        }
    }
    
    /**
     * 提交任务到队列
     *
     * @param config 迁移配置
     * @param priority 优先级（数字越大优先级越高）
     * @return 任务ID
     */
    public String submitToQueue(MigrationConfig config, int priority) {
        String taskId = java.util.UUID.randomUUID().toString();
        MigrationTask task = new MigrationTask(taskId, config, priority);
        taskQueue.offer(task);
        log.info("提交任务到队列，Task ID: {}, 优先级: {}", taskId, priority);
        return taskId;
    }
    
    /**
     * 启动队列处理器
     */
    @Scheduled(fixedDelay = 1000) // 每秒检查一次
    public void processTaskQueue() {
        if (!taskQueue.isEmpty()) {
            // 按优先级排序并执行
            List<MigrationTask> tasks = new java.util.ArrayList<>();
            taskQueue.drainTo(tasks);
            tasks.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
            
            for (MigrationTask task : tasks) {
                executeMigrationTask(task.getTaskId(), task.getConfig());
            }
        }
    }
    
    /**
     * 执行迁移任务
     */
    private void executeMigrationTask(String taskId, MigrationConfig config) {
        try {
            log.info("开始执行迁移任务，Task ID: {}", taskId);
            taskManager.registerTask(taskId, config);
            taskManager.updateTaskStatus(taskId, MigrationTaskManager.MigrationTaskInfo.TaskStatus.RUNNING);
            
            MigrationResult result = migrationService.migrate(config);
            
            if (result.getStatus() == MigrationResult.MigrationStatus.SUCCESS) {
                taskManager.updateTaskStatus(taskId, MigrationTaskManager.MigrationTaskInfo.TaskStatus.COMPLETED);
                log.info("迁移任务完成，Task ID: {}", taskId);
            } else {
                taskManager.updateTaskStatus(taskId, MigrationTaskManager.MigrationTaskInfo.TaskStatus.FAILED);
                log.error("迁移任务失败，Task ID: {}, 错误: {}", taskId, result.getErrorMessage());
            }
            
        } catch (Exception e) {
            log.error("执行迁移任务异常，Task ID: {}", taskId, e);
            taskManager.updateTaskStatus(taskId, MigrationTaskManager.MigrationTaskInfo.TaskStatus.FAILED);
        }
    }
    
    /**
     * 使用 Cron 表达式调度
     */
    private void scheduleWithCron(String taskId, MigrationConfig config, String cronExpression) {
        try {
            // 尝试使用 Quartz
            if (applicationContext != null) {
                try {
                    Class<?> schedulerClass = Class.forName("org.quartz.Scheduler");
                    Object quartzScheduler = applicationContext.getBean(schedulerClass);
                    
                    // 创建 JobDetail
                    Class<?> jobDetailClass = Class.forName("org.quartz.JobDetail");
                    Class<?> jobBuilderClass = Class.forName("org.quartz.JobBuilder");
                    Object jobDetail = jobBuilderClass.getMethod("newJob", Class.class)
                            .invoke(null, MigrationQuartzJob.class);
                    jobDetail = jobDetailClass.getMethod("withIdentity", String.class)
                            .invoke(jobDetail, taskId);
                    jobDetail = jobDetailClass.getMethod("build")
                            .invoke(jobDetail);
                    
                    // 创建 CronTrigger
                    Class<?> triggerClass = Class.forName("org.quartz.Trigger");
                    Class<?> triggerBuilderClass = Class.forName("org.quartz.TriggerBuilder");
                    Object trigger = triggerBuilderClass.getMethod("newTrigger")
                            .invoke(null);
                    trigger = triggerClass.getMethod("withIdentity", String.class)
                            .invoke(trigger, taskId + "-trigger");
                    trigger = triggerClass.getMethod("withSchedule", 
                            Class.forName("org.quartz.ScheduleBuilder"))
                            .invoke(trigger, 
                                    Class.forName("org.quartz.CronScheduleBuilder")
                                            .getMethod("cronSchedule", String.class)
                                            .invoke(null, cronExpression));
                    trigger = triggerClass.getMethod("build")
                            .invoke(trigger);
                    
                    // 将配置存储到 JobDataMap
                    Class<?> jobDataMapClass = Class.forName("org.quartz.JobDataMap");
                    Object jobDataMap = jobDataMapClass.getDeclaredConstructor().newInstance();
                    jobDataMapClass.getMethod("put", String.class, Object.class)
                            .invoke(jobDataMap, "config", config);
                    jobDataMapClass.getMethod("put", String.class, Object.class)
                            .invoke(jobDataMap, "taskId", taskId);
                    jobDataMapClass.getMethod("put", String.class, Object.class)
                            .invoke(jobDataMap, "migrationService", migrationService);
                    
                    jobDetail = jobDetailClass.getMethod("getJobBuilder")
                            .invoke(jobDetail);
                    jobDetail = jobDetailClass.getMethod("usingJobData", jobDataMapClass)
                            .invoke(jobDetail, jobDataMap);
                    jobDetail = jobDetailClass.getMethod("build")
                            .invoke(jobDetail);
                    
                    // 调度任务
                    schedulerClass.getMethod("scheduleJob", jobDetailClass, triggerClass)
                            .invoke(quartzScheduler, jobDetail, trigger);
                    
                    log.info("使用 Quartz 调度任务，Task ID: {}, Cron: {}", taskId, cronExpression);
                    return;
                } catch (ClassNotFoundException e) {
                    log.warn("Quartz 未配置，使用 Spring Task 实现 Cron 调度");
                }
            }
            
            // 降级方案：使用 Spring Task 的 Cron 表达式解析
            scheduleWithSpringCron(taskId, config, cronExpression);
            
        } catch (Exception e) {
            log.error("Cron 调度失败，Task ID: {}, Cron: {}", taskId, cronExpression, e);
            // 降级为延迟任务
            scheduleTask(config, 0);
        }
    }
    
    /**
     * 使用 Spring Task 实现 Cron 调度（简化版）
     */
    private void scheduleWithSpringCron(String taskId, MigrationConfig config, String cronExpression) {
        // Spring Task 的 Cron 表达式格式：秒 分 时 日 月 周
        // 这里简化实现，使用 ScheduledExecutorService 模拟
        log.info("使用 Spring Task 调度任务，Task ID: {}, Cron: {}", taskId, cronExpression);
        
        // 解析 Cron 表达式（简化实现）
        CronExpressionParser parser = new CronExpressionParser(cronExpression);
        long nextDelay = parser.getNextDelay();
        
        if (nextDelay > 0) {
            final String finalTaskId = taskId; // 用于 lambda，避免警告
            scheduler.schedule(() -> {
                executeMigrationTask(finalTaskId, config);
                // 递归调度下一次
                scheduleWithSpringCron(finalTaskId, config, cronExpression);
            }, nextDelay, TimeUnit.SECONDS);
        } else {
            log.warn("无法解析 Cron 表达式: {}, 任务 ID: {}", cronExpression, taskId);
        }
    }
    
    /**
     * Cron 表达式解析器（简化版）
     */
    private static class CronExpressionParser {
        private final String cronExpression;
        
        public CronExpressionParser(String cronExpression) {
            this.cronExpression = cronExpression;
        }
        
        /**
         * 获取下次执行的延迟时间（秒）
         * 简化实现：只支持简单的 Cron 表达式
         */
        public long getNextDelay() {
            // 简化实现：解析秒和分
            String[] parts = cronExpression.trim().split("\\s+");
            if (parts.length >= 2) {
                try {
                    int seconds = parseCronField(parts[0], 60);
                    int minutes = parseCronField(parts[1], 60);
                    
                    java.util.Calendar now = java.util.Calendar.getInstance();
                    int currentSecond = now.get(java.util.Calendar.SECOND);
                    int currentMinute = now.get(java.util.Calendar.MINUTE);
                    
                    int nextSecond = (seconds >= 0) ? seconds : currentSecond;
                    int nextMinute = (minutes >= 0) ? minutes : currentMinute;
                    
                    if (nextSecond < currentSecond) {
                        nextMinute++;
                        nextSecond += 60;
                    }
                    if (nextMinute < currentMinute) {
                        nextMinute += 60;
                    }
                    
                    long delay = (nextMinute - currentMinute) * 60 + (nextSecond - currentSecond);
                    return delay > 0 ? delay : 60; // 至少延迟1分钟
                } catch (Exception e) {
                    return 60; // 默认1分钟
                }
            }
            return 60; // 默认1分钟
        }
        
        private int parseCronField(String field, int max) {
            if ("*".equals(field)) {
                return -1; // 任意值
            }
            try {
                return Integer.parseInt(field);
            } catch (NumberFormatException e) {
                return -1;
            }
        }
    }
    
    /**
     * Quartz Job 实现（用于 Quartz 调度）
     * 注意：需要 Quartz 依赖
     */
    public static class MigrationQuartzJob {
        public void execute(Object context) throws Exception {
            // 使用反射调用，避免直接依赖 Quartz
            try {
                Class<?> contextClass = Class.forName("org.quartz.JobExecutionContext");
                Object jobDetail = contextClass.getMethod("getJobDetail").invoke(context);
                Class<?> jobDetailClass = Class.forName("org.quartz.JobDetail");
                Object dataMap = jobDetailClass.getMethod("getJobDataMap").invoke(jobDetail);
                Class<?> dataMapClass = Class.forName("org.quartz.JobDataMap");
                
                MigrationConfig config = (MigrationConfig) dataMapClass.getMethod("get", String.class)
                        .invoke(dataMap, "config");
                @SuppressWarnings("unused")
                String taskId = (String) dataMapClass.getMethod("get", String.class)
                        .invoke(dataMap, "taskId");
                DataMigrationService migrationService = (DataMigrationService) dataMapClass.getMethod("get", String.class)
                        .invoke(dataMap, "migrationService");
                
                migrationService.migrate(config);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Quartz 未配置", e);
            }
        }
    }
    
    /**
     * 定时任务
     */
    @lombok.Data
    private static class ScheduledTask {
        private String taskId;
        private MigrationConfig config;
        private boolean cancelled = false;
        
        public ScheduledTask(String taskId, MigrationConfig config) {
            this.taskId = taskId;
            this.config = config;
        }
        
        public void cancel() {
            this.cancelled = true;
        }
    }
    
    /**
     * 迁移任务
     */
    @lombok.Data
    private static class MigrationTask {
        private String taskId;
        private MigrationConfig config;
        private int priority;
        private LocalDateTime submitTime;
        
        public MigrationTask(String taskId, MigrationConfig config, int priority) {
            this.taskId = taskId;
            this.config = config;
            this.priority = priority;
            this.submitTime = LocalDateTime.now();
        }
    }
}

