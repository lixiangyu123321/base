package com.lixiangyu.controller;

import com.lixiangyu.common.migration.BinlogListener;
import com.lixiangyu.common.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Binlog 监听控制器
 * 提供 Binlog 监听任务的启动、停止和查询接口
 * 
 * @author lixiangyu
 */
@Slf4j
@RestController
@RequestMapping("/api/migration/binlog")
@RequiredArgsConstructor
public class BinlogListenerController {
    
    private final BinlogListener binlogListener;
    
    /**
     * 启动 Binlog 监听
     */
    @PostMapping("/start")
    public Result<String> startListening(@RequestBody BinlogListenerRequest request) {
        try {
            BinlogListener.BinlogListenerConfig config = BinlogListener.BinlogListenerConfig.builder()
                    .sourceDataSource(request.getSourceDataSource())
                    .targetDataSource(request.getTargetDataSource())
                    .tables(request.getTables())
                    .binlogFile(request.getBinlogFile())
                    .binlogPosition(request.getBinlogPosition())
                    .serverId(request.getServerId())
                    .build();
            
            String taskId = binlogListener.startListening(config);
            return Result.success(taskId);
            
        } catch (Exception e) {
            log.error("启动 Binlog 监听失败", e);
            return Result.fail("启动失败: " + e.getMessage());
        }
    }
    
    /**
     * 停止 Binlog 监听
     */
    @PostMapping("/stop/{taskId}")
    public Result<Void> stopListening(@PathVariable String taskId) {
        try {
            binlogListener.stopListening(taskId);
            return Result.success();
        } catch (Exception e) {
            log.error("停止 Binlog 监听失败", e);
            return Result.fail("停止失败: " + e.getMessage());
        }
    }
    
    /**
     * 查询监听状态
     */
    @GetMapping("/status/{taskId}")
    public Result<Map<String, Object>> getStatus(@PathVariable String taskId) {
        boolean running = binlogListener.isRunning(taskId);
        Map<String, Object> status = new HashMap<>();
        status.put("taskId", taskId);
        status.put("running", running);
        return Result.success(status);
    }
    
    /**
     * Binlog 监听请求
     */
    @lombok.Data
    public static class BinlogListenerRequest {
        private DataSource sourceDataSource;
        private DataSource targetDataSource;
        private java.util.List<String> tables;
        private String binlogFile;
        private Long binlogPosition;
        private Long serverId = 1L;
    }
}

