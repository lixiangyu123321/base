package com.lixiangyu.controller;

import com.lixiangyu.common.migration.CanalListener;
import com.lixiangyu.common.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Canal 监听控制器
 * 提供 Canal 监听任务的启动、停止和查询接口
 * 
 * @author lixiangyu
 */
@Slf4j
@RestController
@RequestMapping("/api/migration/canal")
@RequiredArgsConstructor
public class CanalListenerController {
    
    private final CanalListener canalListener;
    
    /**
     * 启动 Canal 监听
     */
    @PostMapping("/start")
    public Result<String> startListening(@RequestBody CanalListenerRequest request) {
        try {
            CanalListener.CanalListenerConfig config = CanalListener.CanalListenerConfig.builder()
                    .sourceDataSource(request.getSourceDataSource())
                    .targetDataSource(request.getTargetDataSource())
                    .canalServerAddress(request.getCanalServerAddress())
                    .destination(request.getDestination())
                    .username(request.getUsername())
                    .password(request.getPassword())
                    .tables(request.getTables())
                    .subscribeFilter(request.getSubscribeFilter())
                    .batchSize(request.getBatchSize())
                    .build();
            
            String taskId = canalListener.startListening(config);
            return Result.success(taskId);
            
        } catch (Exception e) {
            log.error("启动 Canal 监听失败", e);
            return Result.fail("启动失败: " + e.getMessage());
        }
    }
    
    /**
     * 停止 Canal 监听
     */
    @PostMapping("/stop/{taskId}")
    public Result<Void> stopListening(@PathVariable String taskId) {
        try {
            canalListener.stopListening(taskId);
            return Result.success();
        } catch (Exception e) {
            log.error("停止 Canal 监听失败", e);
            return Result.fail("停止失败: " + e.getMessage());
        }
    }
    
    /**
     * 查询监听状态
     */
    @GetMapping("/status/{taskId}")
    public Result<Map<String, Object>> getStatus(@PathVariable String taskId) {
        boolean running = canalListener.isRunning(taskId);
        Map<String, Object> status = new HashMap<>();
        status.put("taskId", taskId);
        status.put("running", running);
        return Result.success(status);
    }
    
    /**
     * Canal 监听请求
     */
    @lombok.Data
    public static class CanalListenerRequest {
        private DataSource sourceDataSource;
        private DataSource targetDataSource;
        private String canalServerAddress;
        private String destination = "example";
        private String username;
        private String password;
        private java.util.List<String> tables;
        private String subscribeFilter;
        private int batchSize = 1000;
    }
}

