package com.lixiangyu.controller;

import com.lixiangyu.common.migration.DataMigrationService;
import com.lixiangyu.common.migration.MigrationConfig;
import com.lixiangyu.common.migration.MigrationResult;
import com.lixiangyu.common.util.Result;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 数据迁移控制器
 * 提供 RESTful API 接口执行数据迁移
 * 
 * @author lixiangyu
 */
@Slf4j
@RestController
@RequestMapping("/api/migration")
@RequiredArgsConstructor
public class MigrationController {
    
    private final DataMigrationService migrationService;
    
    /**
     * 执行数据迁移
     */
    @PostMapping("/execute")
    public Result<MigrationResult> executeMigration(@RequestBody MigrationRequest request) {
        try {
            log.info("收到数据迁移请求，源: {}, 目标: {}", request.getSourceUrl(), request.getTargetUrl());
            
            // 构建迁移配置
            MigrationConfig config = MigrationConfig.builder()
                    .source(MigrationConfig.DataSourceConfig.builder()
                            .url(request.getSourceUrl())
                            .username(request.getSourceUsername())
                            .password(request.getSourcePassword())
                            .driverClassName(request.getSourceDriver())
                            .build())
                    .target(MigrationConfig.DataSourceConfig.builder()
                            .url(request.getTargetUrl())
                            .username(request.getTargetUsername())
                            .password(request.getTargetPassword())
                            .driverClassName(request.getTargetDriver())
                            .build())
                    .tables(request.getTables())
                    .enableValidation(request.isEnableValidation())
                    .enableIncremental(request.isEnableIncremental())
                    .batchSize(request.getBatchSize() > 0 ? request.getBatchSize() : 1000)
                    .threadCount(request.getThreadCount() > 0 ? request.getThreadCount() : 4)
                    .truncateTarget(request.isTruncateTarget())
                    .mode(request.getMode() != null ? request.getMode() : MigrationConfig.MigrationMode.FULL)
                    .build();
            
            // 执行迁移
            MigrationResult result = migrationService.migrate(config);
            
            // 返回结果
            if (result.getStatus() == MigrationResult.MigrationStatus.SUCCESS) {
                return Result.success(result);
            } else {
                return Result.fail("迁移失败: " + result.getErrorMessage());
            }
            
        } catch (Exception e) {
            log.error("执行数据迁移失败", e);
            return Result.fail("迁移失败: " + e.getMessage());
        }
    }
    
    /**
     * 迁移请求对象
     */
    @Data
    public static class MigrationRequest {
        /**
         * 源数据库配置
         */
        private String sourceUrl;
        private String sourceUsername;
        private String sourcePassword;
        private String sourceDriver;
        
        /**
         * 目标数据库配置
         */
        private String targetUrl;
        private String targetUsername;
        private String targetPassword;
        private String targetDriver;
        
        /**
         * 迁移配置
         */
        private List<String> tables;
        private boolean enableValidation = true;
        private boolean enableIncremental = false;
        private int batchSize = 1000;
        private int threadCount = 4;
        private boolean truncateTarget = false;
        private MigrationConfig.MigrationMode mode = MigrationConfig.MigrationMode.FULL;
    }
}

