package com.lixiangyu.common.migration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据校验器
 * 用于验证源端和目标端数据的一致性
 *
 * 12-31 17:59 数据校验只是逐条记录比较源库和目标库，且全量查询和同步操作，希望分批次以及多线程
 *              或者希望获得更好的校验方法
 * @author lixiangyu
 */
@Slf4j
@Component
public class DataValidator {
    
    /**
     * 校验表数据一致性
     *
     * @param sourceDataSource 源数据源
     * @param targetDataSource 目标数据源
     * @param tableName 表名
     * @param primaryKeys 主键列名列表
     * @return 校验结果
     */
    public MigrationResult.TableValidationDetail validateTable(
            DataSource sourceDataSource,
            DataSource targetDataSource,
            String tableName,
            List<String> primaryKeys) {
        
        log.info("开始校验表: {}", tableName);
        
        MigrationResult.TableValidationDetail.TableValidationDetailBuilder builder = 
                MigrationResult.TableValidationDetail.builder()
                        .tableName(tableName);
        
        try {
            // 1. 校验记录数
            long sourceCount = getRecordCount(sourceDataSource, tableName);
            long targetCount = getRecordCount(targetDataSource, tableName);
            
            builder.sourceRecordCount(sourceCount)
                    .targetRecordCount(targetCount)
                    .consistent(sourceCount == targetCount);
            
            if (sourceCount != targetCount) {
                log.warn("表 {} 记录数不一致，源: {}, 目标: {}", tableName, sourceCount, targetCount);
                return builder.build();
            }
            
            // 2. 校验数据内容（如果主键存在）
            if (primaryKeys != null && !primaryKeys.isEmpty()) {
                List<MigrationResult.InconsistentRecord> inconsistentRecords = 
                        validateDataContent(sourceDataSource, targetDataSource, tableName, primaryKeys);
                
                if (!inconsistentRecords.isEmpty()) {
                    builder.consistent(false)
                            .inconsistentRecords(inconsistentRecords);
                    log.warn("表 {} 发现 {} 条不一致记录", tableName, inconsistentRecords.size());
                } else {
                    builder.consistent(true);
                    log.info("表 {} 数据校验通过", tableName);
                }
            } else {
                // 没有主键，只校验记录数
                builder.consistent(true);
                log.info("表 {} 记录数校验通过（无主键，未进行内容校验）", tableName);
            }
            
        } catch (Exception e) {
            log.error("校验表 {} 失败", tableName, e);
            builder.consistent(false);
        }
        
        return builder.build();
    }
    
    /**
     * 校验数据内容
     */
    private List<MigrationResult.InconsistentRecord> validateDataContent(
            DataSource sourceDataSource,
            DataSource targetDataSource,
            String tableName,
            List<String> primaryKeys) throws SQLException {
        
        List<MigrationResult.InconsistentRecord> inconsistentRecords = new ArrayList<>();
        
        try (Connection sourceConn = sourceDataSource.getConnection();
             Connection targetConn = targetDataSource.getConnection()) {
            
            // 获取所有列名
            List<String> allColumns = getAllColumns(sourceConn, tableName);
            
            // 构建查询 SQL
            String selectColumns = String.join(", ", allColumns);
            String whereClause = primaryKeys.stream()
                    .map(key -> key + " = ?")
                    .reduce((a, b) -> a + " AND " + b)
                    .orElse("");
            
            String sourceSql = "SELECT " + selectColumns + " FROM " + tableName;
            String targetSql = "SELECT " + selectColumns + " FROM " + tableName + " WHERE " + whereClause;

            // TODO 这里一次查源数据库的记录有点太多了吧，分批次分线程
            try (PreparedStatement sourceStmt = sourceConn.prepareStatement(sourceSql);
                 ResultSet sourceRs = sourceStmt.executeQuery()) {
                
                while (sourceRs.next()) {
                    // 构建主键值
                    StringBuilder primaryKeyValue = new StringBuilder();
                    List<Object> primaryKeyValues = new ArrayList<>();
                    
                    for (String key : primaryKeys) {
                        Object value = sourceRs.getObject(key);
                        if (primaryKeyValue.length() > 0) {
                            primaryKeyValue.append("|");
                        }
                        primaryKeyValue.append(value);
                        primaryKeyValues.add(value);
                    }
                    
                    // 查询目标表中的对应记录
                    try (PreparedStatement targetStmt = targetConn.prepareStatement(targetSql)) {
                        for (int i = 0; i < primaryKeyValues.size(); i++) {
                            targetStmt.setObject(i + 1, primaryKeyValues.get(i));
                        }
                        
                        try (ResultSet targetRs = targetStmt.executeQuery()) {
                            if (targetRs.next()) {
                                // 比较字段值
                                Map<String, MigrationResult.FieldDifference> differences = new HashMap<>();
                                
                                for (String column : allColumns) {
                                    Object sourceValue = sourceRs.getObject(column);
                                    Object targetValue = targetRs.getObject(column);
                                    
                                    if (!equals(sourceValue, targetValue)) {
                                        differences.put(column, MigrationResult.FieldDifference.builder()
                                                .fieldName(column)
                                                .sourceValue(sourceValue)
                                                .targetValue(targetValue)
                                                .build());
                                    }
                                }
                                
                                if (!differences.isEmpty()) {
                                    inconsistentRecords.add(MigrationResult.InconsistentRecord.builder()
                                            .primaryKey(primaryKeyValue.toString())
                                            .fieldDifferences(differences)
                                            .build());
                                }
                            } else {
                                // 目标表中不存在该记录
                                inconsistentRecords.add(MigrationResult.InconsistentRecord.builder()
                                        .primaryKey(primaryKeyValue.toString())
                                        .fieldDifferences(new HashMap<>())
                                        .build());
                            }
                        }
                    }
                }
            }
        }
        
        return inconsistentRecords;
    }
    
    /**
     * 获取所有列名
     */
    private List<String> getAllColumns(Connection conn, String tableName) throws SQLException {
        List<String> columns = new ArrayList<>();
        DatabaseMetaData metaData = conn.getMetaData();
        String catalog = conn.getCatalog();
        String schema = conn.getSchema();
        
        try (ResultSet rs = metaData.getColumns(catalog, schema, tableName, null)) {
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME"));
            }
        }
        
        return columns;
    }
    
    /**
     * 获取记录数
     */
    private long getRecordCount(DataSource dataSource, String tableName) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        return 0;
    }
    
    /**
     * 比较两个值是否相等
     */
    private boolean equals(Object a, Object b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.equals(b);
    }
}

