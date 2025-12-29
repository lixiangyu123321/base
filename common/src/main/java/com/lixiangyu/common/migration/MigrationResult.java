package com.lixiangyu.common.migration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 数据迁移结果
 * 
 * @author lixiangyu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MigrationResult {
    
    /**
     * 迁移任务ID
     */
    private String taskId;
    
    /**
     * 迁移状态
     */
    private MigrationStatus status;
    
    /**
     * 开始时间
     */
    private LocalDateTime startTime;
    
    /**
     * 结束时间
     */
    private LocalDateTime endTime;
    
    /**
     * 总耗时（毫秒）
     */
    private Long duration;
    
    /**
     * 总表数
     */
    private Integer totalTables;
    
    /**
     * 成功表数
     */
    private Integer successTables;
    
    /**
     * 失败表数
     */
    private Integer failedTables;
    
    /**
     * 总记录数
     */
    private Long totalRecords;
    
    /**
     * 成功记录数
     */
    private Long successRecords;
    
    /**
     * 失败记录数
     */
    private Long failedRecords;
    
    /**
     * 每个表的迁移详情
     */
    private List<TableMigrationDetail> tableDetails;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * 异常堆栈
     */
    private String stackTrace;
    
    /**
     * 数据校验结果（如果启用）
     */
    private ValidationResult validationResult;
    
    /**
     * 迁移状态枚举
     */
    public enum MigrationStatus {
        /**
         * 等待中
         */
        PENDING,
        
        /**
         * 运行中
         */
        RUNNING,
        
        /**
         * 成功
         */
        SUCCESS,
        
        /**
         * 失败
         */
        FAILED,
        
        /**
         * 部分成功
         */
        PARTIAL_SUCCESS,
        
        /**
         * 已取消
         */
        CANCELLED
    }
    
    /**
     * 表迁移详情
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TableMigrationDetail {
        /**
         * 表名
         */
        private String tableName;
        
        /**
         * 状态
         */
        private MigrationStatus status;
        
        /**
         * 总记录数
         */
        private Long totalRecords;
        
        /**
         * 成功记录数
         */
        private Long successRecords;
        
        /**
         * 失败记录数
         */
        private Long failedRecords;
        
        /**
         * 开始时间
         */
        private LocalDateTime startTime;
        
        /**
         * 结束时间
         */
        private LocalDateTime endTime;
        
        /**
         * 耗时（毫秒）
         */
        private Long duration;
        
        /**
         * 错误信息
         */
        private String errorMessage;
    }
    
    /**
     * 数据校验结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationResult {
        /**
         * 是否通过校验
         */
        private boolean passed;
        
        /**
         * 校验的表数
         */
        private Integer validatedTables;
        
        /**
         * 不一致的表数
         */
        private Integer inconsistentTables;
        
        /**
         * 每个表的校验详情
         */
        private List<TableValidationDetail> tableDetails;
        
        /**
         * 校验耗时（毫秒）
         */
        private Long duration;
    }
    
    /**
     * 表校验详情
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TableValidationDetail {
        /**
         * 表名
         */
        private String tableName;
        
        /**
         * 是否一致
         */
        private boolean consistent;
        
        /**
         * 源表记录数
         */
        private Long sourceRecordCount;
        
        /**
         * 目标表记录数
         */
        private Long targetRecordCount;
        
        /**
         * 不一致的记录详情（可选）
         */
        private List<InconsistentRecord> inconsistentRecords;
    }
    
    /**
     * 不一致记录
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InconsistentRecord {
        /**
         * 主键值
         */
        private String primaryKey;
        
        /**
         * 不一致的字段
         */
        private Map<String, FieldDifference> fieldDifferences;
    }
    
    /**
     * 字段差异
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldDifference {
        /**
         * 字段名
         */
        private String fieldName;
        
        /**
         * 源值
         */
        private Object sourceValue;
        
        /**
         * 目标值
         */
        private Object targetValue;
    }
}

