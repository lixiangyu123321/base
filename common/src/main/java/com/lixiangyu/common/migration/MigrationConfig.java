package com.lixiangyu.common.migration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.sql.DataSource;
import java.util.List;

/**
 * 数据迁移配置
 * 
 * @author lixiangyu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MigrationConfig {
    
    /**
     * 源数据源配置
     */
    private DataSourceConfig source;
    
    /**
     * 目标数据源配置
     */
    private DataSourceConfig target;
    
    /**
     * 迁移的表列表（为空则迁移所有表）
     */
    private List<String> tables;
    
    /**
     * 是否启用数据校验
     */
    @Builder.Default
    private boolean enableValidation = true;
    
    /**
     * 是否启用增量同步
     */
    @Builder.Default
    private boolean enableIncremental = false;
    
    /**
     * 批量大小（每次迁移的记录数）
     */
    @Builder.Default
    private int batchSize = 1000;
    
    /**
     * 并发线程数
     */
    @Builder.Default
    private int threadCount = 4;
    
    /**
     * 是否在迁移前清空目标表
     */
    @Builder.Default
    private boolean truncateTarget = false;
    
    /**
     * 迁移模式：FULL（全量）、INCREMENTAL（增量）、MIXED（混合）
     */
    @Builder.Default
    private MigrationMode mode = MigrationMode.FULL;
    
    /**
     * 是否自动修复不一致数据
     */
    @Builder.Default
    private boolean autoFixInconsistent = false;
    
    /**
     * 是否启用断点续传
     */
    @Builder.Default
    private boolean enableCheckpoint = false;
    
    /**
     * 断点保存间隔（记录数）
     */
    @Builder.Default
    private int checkpointInterval = 10000;
    
    /**
     * 迁移模式枚举
     */
    public enum MigrationMode {
        /**
         * 全量迁移
         */
        FULL,
        
        /**
         * 增量迁移
         */
        INCREMENTAL,
        
        /**
         * 混合模式（先全量后增量）
         */
        MIXED
    }
    
    /**
     * 数据源配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataSourceConfig {
        /**
         * 数据源（优先使用）
         */
        private DataSource dataSource;
        
        /**
         * JDBC URL（如果未提供 dataSource）
         */
        private String url;
        
        /**
         * 用户名
         */
        private String username;
        
        /**
         * 密码
         */
        private String password;
        
        /**
         * 驱动类名
         */
        private String driverClassName;
        
        /**
         * 数据库类型（自动识别或手动指定）
         */
        private DatabaseType databaseType;
        
        /**
         * 数据库操作器类型（JDBC、JDBC_TEMPLATE、MYBATIS、JPA）
         * 默认使用 JDBC
         */
        @Builder.Default
        private OperatorType operatorType = OperatorType.JDBC;
        
        /**
         * 数据库类型枚举
         */
        public enum DatabaseType {
            MYSQL,
            POSTGRESQL,
            ORACLE,
            SQL_SERVER,
            H2,
            MARIADB,
            OTHER
        }
        
        /**
         * 操作器类型枚举
         */
        public enum OperatorType {
            /**
             * JDBC 原生接口
             */
            JDBC,
            
            /**
             * Spring JDBC Template
             */
            JDBC_TEMPLATE,
            
            /**
             * MyBatis
             */
            MYBATIS,
            
            /**
             * JPA
             */
            JPA
        }
    }
}

