package com.lixiangyu.controller;

import com.lixiangyu.common.scheduler.core.JobExecutor;
import com.lixiangyu.common.scheduler.entity.JobConfig;
import com.lixiangyu.common.scheduler.entity.JobLog;
import com.lixiangyu.common.scheduler.service.JobConfigService;
import com.lixiangyu.common.scheduler.service.JobLogService;
import com.lixiangyu.common.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务调度控制器
 * 提供任务管理、执行记录查询、立即执行等接口
 * 
 * @author lixiangyu
 */
@Slf4j
@RestController
@RequestMapping("/api/scheduler")
@RequiredArgsConstructor
public class SchedulerController {
    
    private final JobConfigService jobConfigService;
    private final JobLogService jobLogService;
    private final JobExecutor jobExecutor;
    
    /**
     * 查询任务配置列表
     */
    @GetMapping("/job/config/list")
    public Result<List<JobConfig>> listJobConfigs(
            @RequestParam(required = false) String environment,
            @RequestParam(required = false) String status) {
        try {
            List<JobConfig> configs;
            if (status != null) {
                JobConfig.JobStatus jobStatus = JobConfig.JobStatus.valueOf(status.toUpperCase());
                configs = jobConfigService.listByStatus(jobStatus, environment);
            } else {
                configs = jobConfigService.listAll(environment);
            }
            return Result.success(configs);
        } catch (Exception e) {
            log.error("查询任务配置列表失败", e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 查询任务配置详情
     */
    @GetMapping("/job/config/{id}")
    public Result<JobConfig> getJobConfig(@PathVariable Long id) {
        try {
            JobConfig config = jobConfigService.getById(id);
            if (config == null) {
                return Result.fail("任务配置不存在");
            }
            return Result.success(config);
        } catch (Exception e) {
            log.error("查询任务配置失败，ID: {}", id, e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 创建任务配置
     */
    @PostMapping("/job/config")
    public Result<JobConfig> createJobConfig(@RequestBody JobConfig config) {
        try {
            JobConfig savedConfig = jobConfigService.save(config);
            return Result.success(savedConfig);
        } catch (Exception e) {
            log.error("创建任务配置失败", e);
            return Result.fail("创建失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新任务配置
     */
    @PutMapping("/job/config/{id}")
    public Result<JobConfig> updateJobConfig(@PathVariable Long id, @RequestBody JobConfig config) {
        try {
            config.setId(id);
            JobConfig updatedConfig = jobConfigService.update(config);
            return Result.success(updatedConfig);
        } catch (Exception e) {
            log.error("更新任务配置失败，ID: {}", id, e);
            return Result.fail("更新失败: " + e.getMessage());
        }
    }
    
    /**
     * 删除任务配置
     */
    @DeleteMapping("/job/config/{id}")
    public Result<Void> deleteJobConfig(@PathVariable Long id) {
        try {
            jobConfigService.delete(id);
            return Result.success();
        } catch (Exception e) {
            log.error("删除任务配置失败，ID: {}", id, e);
            return Result.fail("删除失败: " + e.getMessage());
        }
    }
    
    /**
     * 启动任务
     */
    @PostMapping("/job/{id}/start")
    public Result<Void> startJob(@PathVariable Long id) {
        try {
            jobConfigService.enable(id);
            return Result.success();
        } catch (Exception e) {
            log.error("启动任务失败，ID: {}", id, e);
            return Result.fail("启动失败: " + e.getMessage());
        }
    }
    
    /**
     * 停止任务
     */
    @PostMapping("/job/{id}/stop")
    public Result<Void> stopJob(@PathVariable Long id) {
        try {
            jobConfigService.disable(id);
            return Result.success();
        } catch (Exception e) {
            log.error("停止任务失败，ID: {}", id, e);
            return Result.fail("停止失败: " + e.getMessage());
        }
    }
    
    /**
     * 暂停任务
     */
    @PostMapping("/job/{id}/pause")
    public Result<Void> pauseJob(@PathVariable Long id) {
        try {
            jobConfigService.pause(id);
            return Result.success();
        } catch (Exception e) {
            log.error("暂停任务失败，ID: {}", id, e);
            return Result.fail("暂停失败: " + e.getMessage());
        }
    }
    
    /**
     * 恢复任务
     */
    @PostMapping("/job/{id}/resume")
    public Result<Void> resumeJob(@PathVariable Long id) {
        try {
            jobConfigService.resume(id);
            return Result.success();
        } catch (Exception e) {
            log.error("恢复任务失败，ID: {}", id, e);
            return Result.fail("恢复失败: " + e.getMessage());
        }
    }
    
    /**
     * 立即执行任务
     */
    @PostMapping("/job/{id}/execute")
    public Result<Map<String, Object>> executeJob(@PathVariable Long id) {
        try {
            JobConfig config = jobConfigService.getById(id);
            if (config == null) {
                return Result.fail("任务配置不存在");
            }
            
            // 立即执行任务
            JobExecutor.ExecutionResult result = jobExecutor.execute(config);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", result.isSuccess());
            response.put("errorMessage", result.getErrorMessage());
            response.put("jobId", id);
            response.put("jobName", config.getJobName());
            
            return Result.success(response);
        } catch (Exception e) {
            log.error("立即执行任务失败，ID: {}", id, e);
            return Result.fail("执行失败: " + e.getMessage());
        }
    }
    
    /**
     * 查询任务执行记录列表
     */
    @GetMapping("/job/{id}/logs")
    public Result<List<JobLog>> listJobLogs(
            @PathVariable Long id,
            @RequestParam(defaultValue = "50") Integer limit) {
        try {
            List<JobLog> logs = jobLogService.listByJobId(id, limit);
            return Result.success(logs);
        } catch (Exception e) {
            log.error("查询任务执行记录失败，Job ID: {}", id, e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 查询任务执行记录详情
     */
    @GetMapping("/job/log/{logId}")
    public Result<JobLog> getJobLog(@PathVariable Long logId) {
        try {
            JobLog log = jobLogService.getById(logId);
            if (log == null) {
                return Result.fail("执行记录不存在");
            }
            return Result.success(log);
        } catch (Exception e) {
            log.error("查询任务执行记录失败，Log ID: {}", logId, e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据执行ID查询执行记录
     */
    @GetMapping("/job/log/execution/{executionId}")
    public Result<JobLog> getJobLogByExecutionId(@PathVariable String executionId) {
        try {
            JobLog log = jobLogService.getByExecutionId(executionId);
            if (log == null) {
                return Result.fail("执行记录不存在");
            }
            return Result.success(log);
        } catch (Exception e) {
            log.error("查询任务执行记录失败，Execution ID: {}", executionId, e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 查询任务统计信息
     */
    @GetMapping("/job/{id}/statistics")
    public Result<Map<String, Object>> getJobStatistics(@PathVariable Long id) {
        try {
            JobConfig config = jobConfigService.getById(id);
            if (config == null) {
                return Result.fail("任务配置不存在");
            }
            
            // 查询最近的执行记录
            List<JobLog> recentLogs = jobLogService.listByJobId(id, 100);
            
            // 统计信息
            long totalCount = recentLogs.size();
            long successCount = recentLogs.stream()
                    .filter(log -> log.getStatus() == JobLog.ExecutionStatus.SUCCESS)
                    .count();
            long failedCount = recentLogs.stream()
                    .filter(log -> log.getStatus() == JobLog.ExecutionStatus.FAILED)
                    .count();
            
            // 计算平均执行时长
            double avgDuration = recentLogs.stream()
                    .filter(log -> log.getDuration() != null)
                    .mapToLong(JobLog::getDuration)
                    .average()
                    .orElse(0.0);
            
            Map<String, Object> statistics = new HashMap<>();
            statistics.put("jobId", id);
            statistics.put("jobName", config.getJobName());
            statistics.put("totalCount", totalCount);
            statistics.put("successCount", successCount);
            statistics.put("failedCount", failedCount);
            statistics.put("successRate", totalCount > 0 ? (successCount * 100.0 / totalCount) : 0);
            statistics.put("avgDuration", avgDuration);
            statistics.put("status", config.getStatus());
            
            return Result.success(statistics);
        } catch (Exception e) {
            log.error("查询任务统计信息失败，Job ID: {}", id, e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }
}

