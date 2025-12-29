package com.lixiangyu.common.migration;

import java.lang.annotation.*;

/**
 * 热迁移注解
 * 用于标记需要进行热迁移的表或方法
 * 
 * 使用示例：
 * <pre>
 * {@code
 * @HotMigration(
 *     source = "sourceDataSource",
 *     target = "targetDataSource",
 *     tables = {"user", "order"},
 *     enableValidation = true
 * )
 * public void migrateUserData() {
 *     // 迁移逻辑
 * }
 * }
 * </pre>
 * 
 * @author lixiangyu
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface HotMigration {
    
    /**
     * 源数据源 Bean 名称
     */
    String source() default "";
    
    /**
     * 目标数据源 Bean 名称
     */
    String target() default "";
    
    /**
     * 要迁移的表列表（为空则迁移所有表）
     */
    String[] tables() default {};
    
    /**
     * 是否启用数据校验
     */
    boolean enableValidation() default true;
    
    /**
     * 是否启用增量同步
     */
    boolean enableIncremental() default false;
    
    /**
     * 批量大小
     */
    int batchSize() default 1000;
    
    /**
     * 并发线程数
     */
    int threadCount() default 4;
    
    /**
     * 是否在迁移前清空目标表
     */
    boolean truncateTarget() default false;
    
    /**
     * 迁移模式
     */
    MigrationConfig.MigrationMode mode() default MigrationConfig.MigrationMode.FULL;
}

