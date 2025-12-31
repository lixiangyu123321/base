package com.lixiangyu.common.migration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
// AspectJ 支持（可选，如果不需要注解方式可以注释掉）
// import org.aspectj.lang.ProceedingJoinPoint;
// import org.aspectj.lang.annotation.Around;
// import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * 热迁移切面
 * 支持通过 @HotMigration 注解自动执行数据迁移
 *
 * TODO 未支持热迁移注解
 * TODO 将来改写
 * @author lixiangyu
 */
@Slf4j
// @Aspect  // 如果需要使用注解方式，需要添加 AspectJ 依赖并取消注释
@Component
@RequiredArgsConstructor
public class HotMigrationAspect {
    
    @Autowired
    private ApplicationContext applicationContext;
    
    @Autowired
    private DataMigrationService migrationService;
    
    /**
     * 执行热迁移（手动调用方式）
     * 如果需要注解方式，需要添加 AspectJ 依赖并启用 @Around 方法
     */
    public MigrationResult executeMigration(HotMigration hotMigration) {
        log.info("开始执行数据迁移");
        
        try {
            // 构建迁移配置
            MigrationConfig config = buildMigrationConfig(hotMigration);
            
            // 执行迁移
            MigrationResult result = migrationService.migrate(config);
            
            // 记录结果
            logMigrationResult(result);
            
            return result;
            
        } catch (Exception e) {
            log.error("执行热迁移失败", e);
            throw new RuntimeException("数据迁移失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 构建迁移配置
     */
    private MigrationConfig buildMigrationConfig(HotMigration annotation) {
        MigrationConfig.MigrationConfigBuilder builder = MigrationConfig.builder();
        
        // 源数据源
        if (annotation.source() != null && !annotation.source().isEmpty()) {
            DataSource sourceDataSource = applicationContext.getBean(annotation.source(), DataSource.class);
            builder.source(MigrationConfig.DataSourceConfig.builder()
                    .dataSource(sourceDataSource)
                    .build());
        }
        
        // 目标数据源
        if (annotation.target() != null && !annotation.target().isEmpty()) {
            DataSource targetDataSource = applicationContext.getBean(annotation.target(), DataSource.class);
            builder.target(MigrationConfig.DataSourceConfig.builder()
                    .dataSource(targetDataSource)
                    .build());
        }
        
        // 其他配置
        if (annotation.tables().length > 0) {
            builder.tables(java.util.Arrays.asList(annotation.tables()));
        }
        
        builder.enableValidation(annotation.enableValidation())
                .enableIncremental(annotation.enableIncremental())
                .batchSize(annotation.batchSize())
                .threadCount(annotation.threadCount())
                .truncateTarget(annotation.truncateTarget())
                .mode(annotation.mode());
        
        return builder.build();
    }
    
    /**
     * 记录迁移结果
     */
    private void logMigrationResult(MigrationResult result) {
        log.info("数据迁移完成 - Task ID: {}, 状态: {}, 总表数: {}, 成功: {}, 失败: {}, 总记录数: {}, 成功: {}, 失败: {}, 耗时: {}ms",
                result.getTaskId(),
                result.getStatus(),
                result.getTotalTables(),
                result.getSuccessTables(),
                result.getFailedTables(),
                result.getTotalRecords(),
                result.getSuccessRecords(),
                result.getFailedRecords(),
                result.getDuration());
        
        if (result.getValidationResult() != null) {
            MigrationResult.ValidationResult validation = result.getValidationResult();
            log.info("数据校验完成 - 校验表数: {}, 不一致表数: {}, 是否通过: {}, 耗时: {}ms",
                    validation.getValidatedTables(),
                    validation.getInconsistentTables(),
                    validation.isPassed(),
                    validation.getDuration());
        }
    }
}

