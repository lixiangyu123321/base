package com.lixiangyu.common.migration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 迁移任务管理器
 * 管理迁移任务的进度、状态和断点信息
 * 
 * 功能特性：
 * 1. 任务进度跟踪
 * 2. 断点续传支持
 * 3. 任务状态管理
 * 4. 任务查询接口
 * 
 * @author lixiangyu
 */
@Slf4j
@Component
public class MigrationTaskManager {
    
    /**
     * 任务信息映射（Task ID -> 任务信息）
     */
    private final Map<String, MigrationTaskInfo> taskInfos = new ConcurrentHashMap<>();
    
    /**
     * 任务进度映射（Task ID -> 进度信息）
     */
    private final Map<String, MigrationProgress> taskProgresses = new ConcurrentHashMap<>();
    
    /**
     * 注册迁移任务
     *
     * @param taskId 任务ID
     * @param config 迁移配置
     */
    public void registerTask(String taskId, MigrationConfig config) {
        MigrationTaskInfo taskInfo = MigrationTaskInfo.builder()
                .taskId(taskId)
                .config(config)
                .status(MigrationTaskInfo.TaskStatus.PENDING)
                .createTime(LocalDateTime.now())
                .build();
        
        taskInfos.put(taskId, taskInfo);
        
        MigrationProgress progress = MigrationProgress.builder()
                .taskId(taskId)
                .totalTables(0)
                .completedTables(0)
                .totalRecords(0L)
                .completedRecords(0L)
                .startTime(LocalDateTime.now())
                .build();
        
        taskProgresses.put(taskId, progress);
        
        log.info("注册迁移任务，Task ID: {}", taskId);
    }
    
    /**
     * 更新任务状态
     *
     * @param taskId 任务ID
     * @param status 状态
     */
    public void updateTaskStatus(String taskId, MigrationTaskInfo.TaskStatus status) {
        MigrationTaskInfo taskInfo = taskInfos.get(taskId);
        if (taskInfo != null) {
            taskInfo.setStatus(status);
            if (status == MigrationTaskInfo.TaskStatus.COMPLETED || 
                status == MigrationTaskInfo.TaskStatus.FAILED) {
                taskInfo.setEndTime(LocalDateTime.now());
            }
            log.debug("更新任务状态，Task ID: {}, 状态: {}", taskId, status);
        }
    }
    
    /**
     * 更新任务进度
     *
     * @param taskId 任务ID
     * @param completedTables 已完成表数
     * @param completedRecords 已完成记录数
     */
    public void updateProgress(String taskId, int completedTables, long completedRecords) {
        MigrationProgress progress = taskProgresses.get(taskId);
        if (progress != null) {
            progress.setCompletedTables(completedTables);
            progress.setCompletedRecords(completedRecords);
            progress.setUpdateTime(LocalDateTime.now());
            
            // 计算进度百分比
            if (progress.getTotalTables() > 0) {
                progress.setTableProgress((double) completedTables / progress.getTotalTables() * 100);
            }
            if (progress.getTotalRecords() > 0) {
                progress.setRecordProgress((double) completedRecords / progress.getTotalRecords() * 100);
            }
        }
    }
    
    /**
     * 设置总表数和总记录数
     *
     * @param taskId 任务ID
     * @param totalTables 总表数
     * @param totalRecords 总记录数
     */
    public void setTotal(String taskId, int totalTables, long totalRecords) {
        MigrationProgress progress = taskProgresses.get(taskId);
        if (progress != null) {
            progress.setTotalTables(totalTables);
            progress.setTotalRecords(totalRecords);
        }
    }
    
    /**
     * 保存断点信息
     *
     * @param taskId 任务ID
     * @param checkpoint 断点信息
     */
    public void saveCheckpoint(String taskId, MigrationCheckpoint checkpoint) {
        MigrationTaskInfo taskInfo = taskInfos.get(taskId);
        if (taskInfo != null) {
            taskInfo.setCheckpoint(checkpoint);
            log.debug("保存断点信息，Task ID: {}, 表: {}, 位置: {}", 
                    taskId, checkpoint.getTableName(), checkpoint.getPosition());
        }
    }
    
    /**
     * 获取断点信息
     *
     * @param taskId 任务ID
     * @param tableName 表名
     * @return 断点信息
     */
    public MigrationCheckpoint getCheckpoint(String taskId, String tableName) {
        MigrationTaskInfo taskInfo = taskInfos.get(taskId);
        if (taskInfo != null && taskInfo.getCheckpoint() != null) {
            MigrationCheckpoint checkpoint = taskInfo.getCheckpoint();
            if (tableName.equals(checkpoint.getTableName())) {
                return checkpoint;
            }
        }
        return null;
    }
    
    /**
     * 获取任务信息
     *
     * @param taskId 任务ID
     * @return 任务信息
     */
    public MigrationTaskInfo getTaskInfo(String taskId) {
        return taskInfos.get(taskId);
    }
    
    /**
     * 获取任务进度
     *
     * @param taskId 任务ID
     * @return 进度信息
     */
    public MigrationProgress getProgress(String taskId) {
        return taskProgresses.get(taskId);
    }
    
    /**
     * 删除任务
     *
     * @param taskId 任务ID
     */
    public void removeTask(String taskId) {
        taskInfos.remove(taskId);
        taskProgresses.remove(taskId);
        log.info("删除迁移任务，Task ID: {}", taskId);
    }
    
    /**
     * 迁移任务信息
     */
    @lombok.Data
    @lombok.Builder
    public static class MigrationTaskInfo {
        private String taskId;
        private MigrationConfig config;
        private TaskStatus status;
        private LocalDateTime createTime;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private MigrationCheckpoint checkpoint;
        private String errorMessage;
        
        public enum TaskStatus {
            PENDING,    // 等待中
            RUNNING,    // 运行中
            PAUSED,     // 已暂停
            COMPLETED,  // 已完成
            FAILED,     // 失败
            CANCELLED   // 已取消
        }
    }
    
    /**
     * 迁移进度信息
     */
    @lombok.Data
    @lombok.Builder
    public static class MigrationProgress {
        private String taskId;
        private int totalTables;
        private int completedTables;
        private long totalRecords;
        private long completedRecords;
        private double tableProgress;    // 表进度百分比
        private double recordProgress;    // 记录进度百分比
        private LocalDateTime startTime;
        private LocalDateTime updateTime;
    }
    
    /**
     * 迁移断点信息
     */
    @lombok.Data
    @lombok.Builder
    public static class MigrationCheckpoint {
        /**
         * 表名
         */
        private String tableName;
        
        /**
         * 位置（主键值或偏移量）
         */
        private String position;
        
        /**
         * Binlog 文件名（用于增量同步）
         */
        private String binlogFile;
        
        /**
         * Binlog 位置（用于增量同步）
         */
        private Long binlogPosition;
        
        /**
         * 已迁移记录数
         */
        private long migratedRecords;
        
        /**
         * 保存时间
         */
        private LocalDateTime saveTime;
    }
}

