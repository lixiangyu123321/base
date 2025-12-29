package com.lixiangyu.controller;

import com.lixiangyu.common.migration.MigrationTaskManager;
import com.lixiangyu.common.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 迁移进度查询控制器
 * 提供迁移任务进度查询接口
 * 
 * @author lixiangyu
 */
@Slf4j
@RestController
@RequestMapping("/api/migration/progress")
@RequiredArgsConstructor
public class MigrationProgressController {
    
    private final MigrationTaskManager taskManager;
    
    /**
     * 查询任务进度
     */
    @GetMapping("/{taskId}")
    public Result<MigrationTaskManager.MigrationProgress> getProgress(@PathVariable String taskId) {
        MigrationTaskManager.MigrationProgress progress = taskManager.getProgress(taskId);
        if (progress == null) {
            return Result.fail("任务不存在: " + taskId);
        }
        return Result.success(progress);
    }
    
    /**
     * 查询任务信息
     */
    @GetMapping("/task/{taskId}")
    public Result<MigrationTaskManager.MigrationTaskInfo> getTaskInfo(@PathVariable String taskId) {
        MigrationTaskManager.MigrationTaskInfo taskInfo = taskManager.getTaskInfo(taskId);
        if (taskInfo == null) {
            return Result.fail("任务不存在: " + taskId);
        }
        return Result.success(taskInfo);
    }
    
    /**
     * 查询断点信息
     */
    @GetMapping("/checkpoint/{taskId}")
    public Result<MigrationTaskManager.MigrationCheckpoint> getCheckpoint(
            @PathVariable String taskId,
            @RequestParam String tableName) {
        MigrationTaskManager.MigrationCheckpoint checkpoint = 
                taskManager.getCheckpoint(taskId, tableName);
        if (checkpoint == null) {
            return Result.fail("断点不存在: " + taskId + ", " + tableName);
        }
        return Result.success(checkpoint);
    }
}

