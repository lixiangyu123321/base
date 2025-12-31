package com.lixiangyu.common.migration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 数据迁移服务
 * 支持同构和异构数据库的热迁移，业务不停服
 * 
 * 功能特性：
 * 1. 全量迁移：一次性迁移所有数据
 * 2. 增量迁移：实时同步增量数据
 * 3. 混合模式：先全量后增量
 * 4. 数据校验：验证迁移数据的一致性
 * 5. 并发迁移：支持多线程并发迁移
 * 6. 断点续传：支持迁移任务中断后继续
 * TODO 迁移过程需要持久化
 *
 * *** 12-31 17:57 该数据迁移功能只能实现两个数据库的全量迁移，且相应构造的SQL语句较为简单，并且充斥者大量的全量查询于同步操作，后续希望优化为分批次和多线程
 *                  并且希望实现增量迁移功能
 * @author lixiangyu
 */
@Slf4j
@Service
public class DataMigrationService {
    
    /**
     * 执行数据迁移
     *
     * @param config 迁移配置
     * @return 迁移结果
     */
    public MigrationResult migrate(MigrationConfig config) {
        String taskId = UUID.randomUUID().toString();
        log.info("开始数据迁移任务，Task ID: {}", taskId);
        
        MigrationResult.MigrationStatus status = MigrationResult.MigrationStatus.RUNNING;
        LocalDateTime startTime = LocalDateTime.now();
        MigrationResult result = MigrationResult.builder()
                .taskId(taskId)
                .status(status)
                .startTime(startTime)
                .tableDetails(new ArrayList<>())
                .build();
        
        DataSource sourceDataSource = null;
        DataSource targetDataSource = null;
        
        try {
            // 1. 创建数据源
            sourceDataSource = createDataSource(config.getSource());
            targetDataSource = createDataSource(config.getTarget());
            
            // 2. 获取要迁移的表列表
            List<String> tables = getTablesToMigrate(sourceDataSource, config);
            result.setTotalTables(tables.size());
            log.info("需要迁移的表数量: {}", tables.size());
            
            // 3. 执行迁移（使用 final 变量供 lambda 使用）
            final DataSource finalSourceDataSource = sourceDataSource;
            final DataSource finalTargetDataSource = targetDataSource;
            ExecutorService executor = Executors.newFixedThreadPool(config.getThreadCount());
            List<Future<MigrationResult.TableMigrationDetail>> futures = new ArrayList<>();

            // 每个表对应一个线程
            for (String table : tables) {
                final String tableName = table;
                Future<MigrationResult.TableMigrationDetail> future = executor.submit(() -> 
                    migrateTable(finalSourceDataSource, finalTargetDataSource, tableName, config)
                );
                futures.add(future);
            }
            
            // 4. 收集结果
            // TODO 这里应该使用原子类吧
            long totalRecords = 0;
            long successRecords = 0;
            long failedRecords = 0;
            int successTables = 0;
            int failedTables = 0;
            
            for (Future<MigrationResult.TableMigrationDetail> future : futures) {
                try {
                    MigrationResult.TableMigrationDetail detail = future.get();
                    result.getTableDetails().add(detail);
                    
                    totalRecords += detail.getTotalRecords() != null ? detail.getTotalRecords() : 0;
                    successRecords += detail.getSuccessRecords() != null ? detail.getSuccessRecords() : 0;
                    failedRecords += detail.getFailedRecords() != null ? detail.getFailedRecords() : 0;
                    
                    if (detail.getStatus() == MigrationResult.MigrationStatus.SUCCESS) {
                        successTables++;
                    } else {
                        failedTables++;
                    }
                } catch (Exception e) {
                    log.error("获取迁移结果失败", e);
                    failedTables++;
                }
            }
            
            executor.shutdown();
            
            result.setTotalRecords(totalRecords);
            result.setSuccessRecords(successRecords);
            result.setFailedRecords(failedRecords);
            result.setSuccessTables(successTables);
            result.setFailedTables(failedTables);
            
            // 5. 数据校验
            if (config.isEnableValidation()) {
                log.info("开始数据校验");
                MigrationResult.ValidationResult validationResult = validateData(
                        finalSourceDataSource, finalTargetDataSource, tables, config);
                result.setValidationResult(validationResult);
            }
            
            // 6. 确定最终状态
            if (failedTables == 0) {
                status = MigrationResult.MigrationStatus.SUCCESS;
            } else if (successTables > 0) {
                status = MigrationResult.MigrationStatus.PARTIAL_SUCCESS;
            } else {
                status = MigrationResult.MigrationStatus.FAILED;
            }
            
        } catch (Exception e) {
            log.error("数据迁移失败，Task ID: {}", taskId, e);
            status = MigrationResult.MigrationStatus.FAILED;
            result.setErrorMessage(e.getMessage());
            result.setStackTrace(getStackTrace(e));
        } finally {
            // 关闭数据源
            closeDataSource(sourceDataSource);
            closeDataSource(targetDataSource);
            
            LocalDateTime endTime = LocalDateTime.now();
            result.setStatus(status);
            result.setEndTime(endTime);
            result.setDuration(java.time.Duration.between(startTime, endTime).toMillis());
            
            log.info("数据迁移任务完成，Task ID: {}, 状态: {}, 耗时: {}ms", 
                    taskId, status, result.getDuration());
        }
        
        return result;
    }
    
    /**
     * 迁移单个表
     */
    private MigrationResult.TableMigrationDetail migrateTable(
            DataSource sourceDataSource, 
            DataSource targetDataSource, 
            String tableName, 
            MigrationConfig config) {
        
        LocalDateTime startTime = LocalDateTime.now();
        MigrationResult.TableMigrationDetail detail = MigrationResult.TableMigrationDetail.builder()
                .tableName(tableName)
                .status(MigrationResult.MigrationStatus.RUNNING)
                .startTime(startTime)
                .build();
        
        try {
            log.info("开始迁移表: {}", tableName);
            
            // 1. 获取表结构
            TableStructure sourceStructure = getTableStructure(sourceDataSource, tableName);
            TableStructure targetStructure = getTableStructure(targetDataSource, tableName);
            
            // 2. 如果目标表不存在，创建表
            if (targetStructure == null) {
                createTable(targetDataSource, tableName, sourceStructure);
                targetStructure = getTableStructure(targetDataSource, tableName);
            }
            
            // 3. 如果配置了清空目标表
            if (config.isTruncateTarget()) {
                truncateTable(targetDataSource, tableName);
            }
            
            // 4. 迁移数据
            long totalRecords = getRecordCount(sourceDataSource, tableName);
            detail.setTotalRecords(totalRecords);

            // TODO 迁移过程一定不能是一次性迁移所有记录， 一定要分批次分线程
            if (totalRecords > 0) {
                long successRecords = copyData(
                        sourceDataSource, targetDataSource, 
                        tableName, sourceStructure, targetStructure, config);
                detail.setSuccessRecords(successRecords);
                detail.setFailedRecords(totalRecords - successRecords);
            } else {
                detail.setSuccessRecords(0L);
                detail.setFailedRecords(0L);
            }
            
            detail.setStatus(MigrationResult.MigrationStatus.SUCCESS);
            log.info("表 {} 迁移完成，总记录数: {}, 成功: {}", 
                    tableName, totalRecords, detail.getSuccessRecords());
            
        } catch (Exception e) {
            log.error("迁移表 {} 失败", tableName, e);
            detail.setStatus(MigrationResult.MigrationStatus.FAILED);
            detail.setErrorMessage(e.getMessage());
        } finally {
            LocalDateTime endTime = LocalDateTime.now();
            detail.setEndTime(endTime);
            detail.setDuration(java.time.Duration.between(startTime, endTime).toMillis());
        }
        
        return detail;
    }
    
    /**
     * 复制数据
     */
    private long copyData(
            DataSource sourceDataSource, 
            DataSource targetDataSource,
            String tableName,
            TableStructure sourceStructure,
            TableStructure targetStructure,
            MigrationConfig config) throws SQLException {
        
        long successCount = 0;
        
        try (Connection sourceConn = sourceDataSource.getConnection();
             Connection targetConn = targetDataSource.getConnection()) {
            
            // 获取列映射
            List<ColumnMapping> columnMappings = mapColumns(sourceStructure, targetStructure);
            
            // 构建 SELECT 语句
            String selectSql = buildSelectSql(tableName, sourceStructure);
            
            // 构建 INSERT 语句
            String insertSql = buildInsertSql(tableName, targetStructure, columnMappings);
            
            try (PreparedStatement selectStmt = sourceConn.prepareStatement(selectSql);
                 PreparedStatement insertStmt = targetConn.prepareStatement(insertSql)) {
                
                ResultSet rs = selectStmt.executeQuery();
                int batchCount = 0;
                
                while (rs.next()) {
                    // 设置 INSERT 参数
                    setInsertParameters(insertStmt, rs, columnMappings, sourceStructure, targetStructure);
                    insertStmt.addBatch();
                    batchCount++;
                    
                    // 批量执行
                    if (batchCount >= config.getBatchSize()) {
                        int[] results = insertStmt.executeBatch();
                        for (int result : results) {
                            if (result > 0) {
                                successCount += result;
                            }
                        }
                        insertStmt.clearBatch();
                        batchCount = 0;
                    }
                }
                
                // 执行剩余的批次
                if (batchCount > 0) {
                    int[] results = insertStmt.executeBatch();
                    for (int result : results) {
                        if (result > 0) {
                            successCount += result;
                        }
                    }
                }
            }
        }
        
        return successCount;
    }
    
    /**
     * 数据校验
     * 通过比较前后数据量的大小进行迁移校验
     * 支持不一致数据的自动修复
     */
    private MigrationResult.ValidationResult validateData(
            DataSource sourceDataSource,
            DataSource targetDataSource,
            List<String> tables,
            MigrationConfig config) {
        
        LocalDateTime startTime = LocalDateTime.now();
        List<MigrationResult.TableValidationDetail> tableDetails = new ArrayList<>();
        int validatedTables = 0;
        int inconsistentTables = 0;
        
        try {
            // 使用 DataValidator 进行详细校验
            DataValidator validator = new DataValidator();

            // TODO 不同表的校验也可以使用多线程的
            for (String tableName : tables) {
                try {
                    // 1. 记录数校验
                    long sourceCount = getRecordCount(sourceDataSource, tableName);
                    long targetCount = getRecordCount(targetDataSource, tableName);
                    
                    boolean consistent = sourceCount == targetCount;
                    
                    // 2. 如果记录数不一致，进行详细校验
                    List<MigrationResult.InconsistentRecord> inconsistentRecords = null;
                    if (!consistent) {
                        log.warn("表 {} 记录数不一致，源: {}, 目标: {}", tableName, sourceCount, targetCount);
                        
                        // 获取主键列表
                        List<String> primaryKeys = getPrimaryKeys(sourceDataSource, tableName);
                        
                        // 详细校验（如果有主键）
                        // TODO 其实就是逐条记录比较，有没有更好的方法
                        if (primaryKeys != null && !primaryKeys.isEmpty()) {
                            MigrationResult.TableValidationDetail detail = validator.validateTable(
                                    sourceDataSource, targetDataSource, tableName, primaryKeys);
                            inconsistentRecords = detail.getInconsistentRecords();
                            
                            // 3. 自动修复不一致数据（可选）
                            if (config.isAutoFixInconsistent() && inconsistentRecords != null) {
                                fixInconsistentData(sourceDataSource, targetDataSource, 
                                        tableName, inconsistentRecords, primaryKeys);
                            }
                        }
                        
                        inconsistentTables++;
                    }
                    
                    tableDetails.add(MigrationResult.TableValidationDetail.builder()
                            .tableName(tableName)
                            .consistent(consistent)
                            .sourceRecordCount(sourceCount)
                            .targetRecordCount(targetCount)
                            .inconsistentRecords(inconsistentRecords)
                            .build());
                    
                    validatedTables++;
                    
                } catch (Exception e) {
                    log.error("校验表 {} 失败", tableName, e);
                }
            }
        } catch (Exception e) {
            log.error("数据校验失败", e);
        }
        
        LocalDateTime endTime = LocalDateTime.now();
        long duration = java.time.Duration.between(startTime, endTime).toMillis();
        
        return MigrationResult.ValidationResult.builder()
                .passed(inconsistentTables == 0)
                .validatedTables(validatedTables)
                .inconsistentTables(inconsistentTables)
                .tableDetails(tableDetails)
                .duration(duration)
                .build();
    }
    
    /**
     * 修复不一致数据
     */
    private void fixInconsistentData(
            DataSource sourceDataSource,
            DataSource targetDataSource,
            String tableName,
            List<MigrationResult.InconsistentRecord> inconsistentRecords,
            List<String> primaryKeys) {
        
        log.info("开始修复表 {} 的不一致数据，共 {} 条", tableName, inconsistentRecords.size());
        
        try (Connection sourceConn = sourceDataSource.getConnection();
             Connection targetConn = targetDataSource.getConnection()) {
            
            for (MigrationResult.InconsistentRecord record : inconsistentRecords) {
                try {
                    // 从源库获取完整数据
                    String selectSql = buildSelectByPrimaryKeySql(tableName, primaryKeys);
                    String primaryKeyValue = record.getPrimaryKey();
                    
                    try (PreparedStatement selectStmt = sourceConn.prepareStatement(selectSql)) {
                        // 设置主键参数
                        String[] keyValues = primaryKeyValue.split("\\|");
                        for (int i = 0; i < keyValues.length; i++) {
                            selectStmt.setObject(i + 1, keyValues[i]);
                        }
                        
                        try (ResultSet rs = selectStmt.executeQuery()) {
                            if (rs.next()) {
                                // 获取表结构
                                TableStructure targetStructure = getTableStructure(targetDataSource, tableName);
                                
                                // 构建 REPLACE INTO 或 UPDATE 语句
                                String replaceSql = buildReplaceSql(tableName, targetStructure);
                                
                                try (PreparedStatement replaceStmt = targetConn.prepareStatement(replaceSql)) {
                                    // 设置参数
                                    setInsertParameters(replaceStmt, rs, 
                                            mapColumns(getTableStructure(sourceDataSource, tableName), targetStructure),
                                            getTableStructure(sourceDataSource, tableName),
                                            targetStructure);
                                    
                                    replaceStmt.executeUpdate();
                                    log.debug("修复不一致记录，表: {}, 主键: {}", tableName, primaryKeyValue);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("修复不一致记录失败，表: {}, 主键: {}", tableName, record.getPrimaryKey(), e);
                }
            }
            
        } catch (Exception e) {
            log.error("修复不一致数据失败，表: {}", tableName, e);
        }
    }
    
    /**
     * 构建根据主键查询的 SQL
     */
    private String buildSelectByPrimaryKeySql(String tableName, List<String> primaryKeys) {
        String whereClause = primaryKeys.stream()
                .map(key -> key + " = ?")
                .collect(Collectors.joining(" AND "));
        return "SELECT * FROM " + tableName + " WHERE " + whereClause;
    }
    
    /**
     * 构建 REPLACE INTO SQL
     */
    private String buildReplaceSql(String tableName, TableStructure structure) {
        String columns = structure.getColumns().stream()
                .map(TableColumn::getName)
                .collect(Collectors.joining(", "));
        String values = structure.getColumns().stream()
                .map(c -> "?")
                .collect(Collectors.joining(", "));
        return "REPLACE INTO " + tableName + " (" + columns + ") VALUES (" + values + ")";
    }
    
    /**
     * 获取主键列表
     */
    private List<String> getPrimaryKeys(DataSource dataSource, String tableName) throws SQLException {
        List<String> primaryKeys = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            String catalog = conn.getCatalog();
            String schema = conn.getSchema();
            
            try (ResultSet rs = metaData.getPrimaryKeys(catalog, schema, tableName)) {
                while (rs.next()) {
                    primaryKeys.add(rs.getString("COLUMN_NAME"));
                }
            }
        }
        return primaryKeys;
    }
    
    /**
     * 创建数据源
     * 基于数据库连接池创建数据源
     */
    private DataSource createDataSource(MigrationConfig.DataSourceConfig config) {
        if (config.getDataSource() != null) {
            return config.getDataSource();
        }
        
        // 使用反射创建 HikariDataSource，避免直接依赖
        try {
            Class<?> hikariConfigClass = Class.forName("com.zaxxer.hikari.HikariConfig");
            Class<?> hikariDataSourceClass = Class.forName("com.zaxxer.hikari.HikariDataSource");
            
            Object hikariConfig = hikariConfigClass.getDeclaredConstructor().newInstance();
            hikariConfigClass.getMethod("setJdbcUrl", String.class).invoke(hikariConfig, config.getUrl());
            hikariConfigClass.getMethod("setUsername", String.class).invoke(hikariConfig, config.getUsername());
            hikariConfigClass.getMethod("setPassword", String.class).invoke(hikariConfig, config.getPassword());
            
            if (StringUtils.hasText(config.getDriverClassName())) {
                hikariConfigClass.getMethod("setDriverClassName", String.class)
                        .invoke(hikariConfig, config.getDriverClassName());
            }
            
            hikariConfigClass.getMethod("setMaximumPoolSize", int.class).invoke(hikariConfig, 10);
            hikariConfigClass.getMethod("setMinimumIdle", int.class).invoke(hikariConfig, 2);
            hikariConfigClass.getMethod("setConnectionTimeout", long.class).invoke(hikariConfig, 30000L);
            
            return (DataSource) hikariDataSourceClass.getConstructor(hikariConfigClass).newInstance(hikariConfig);
            
        } catch (Exception e) {
            log.error("创建 HikariDataSource 失败，尝试使用 DriverManager", e);
            // 降级方案：使用 DriverManager
            return new DriverManagerDataSource(config.getUrl(), config.getUsername(), config.getPassword());
        }
    }
    
    /**
     * 简单的 DriverManager 数据源实现
     */
    private static class DriverManagerDataSource implements DataSource {
        private final String url;
        private final String username;
        private final String password;
        
        public DriverManagerDataSource(String url, String username, String password) {
            this.url = url;
            this.username = username;
            this.password = password;
        }
        
        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }
        
        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }
        
        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("Not supported");
        }
        
        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return false;
        }
        
        @Override
        public java.io.PrintWriter getLogWriter() throws SQLException {
            return null;
        }
        
        @Override
        public void setLogWriter(java.io.PrintWriter out) throws SQLException {
        }
        
        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
        }
        
        @Override
        public int getLoginTimeout() throws SQLException {
            return 0;
        }
        
        @Override
        public java.util.logging.Logger getParentLogger() {
            return null;
        }
    }
    
    /**
     * 获取要迁移的表列表
     */
    private List<String> getTablesToMigrate(DataSource dataSource, MigrationConfig config) throws SQLException {
        if (config.getTables() != null && !config.getTables().isEmpty()) {
            return config.getTables();
        }
        
        List<String> tables = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            String catalog = conn.getCatalog();
            String schema = conn.getSchema();
            
            try (ResultSet rs = metaData.getTables(catalog, schema, null, new String[]{"TABLE"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    tables.add(tableName);
                }
            }
        }
        
        return tables;
    }
    
    /**
     * 获取表结构
     */
    private TableStructure getTableStructure(DataSource dataSource, String tableName) throws SQLException {
        TableStructure structure = new TableStructure();
        structure.setTableName(tableName);
        structure.setColumns(new ArrayList<>());
        
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            String catalog = conn.getCatalog();
            String schema = conn.getSchema();
            
            try (ResultSet rs = metaData.getColumns(catalog, schema, tableName, null)) {
                while (rs.next()) {
                    TableColumn column = new TableColumn();
                    column.setName(rs.getString("COLUMN_NAME"));
                    column.setType(rs.getInt("DATA_TYPE"));
                    column.setTypeName(rs.getString("TYPE_NAME"));
                    column.setSize(rs.getInt("COLUMN_SIZE"));
                    column.setNullable(rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
                    column.setDefaultValue(rs.getString("COLUMN_DEF"));
                    structure.getColumns().add(column);
                }
            }
            
            // 获取主键
            try (ResultSet rs = metaData.getPrimaryKeys(catalog, schema, tableName)) {
                List<String> primaryKeys = new ArrayList<>();
                while (rs.next()) {
                    primaryKeys.add(rs.getString("COLUMN_NAME"));
                }
                structure.setPrimaryKeys(primaryKeys);
            }
        }
        
        return structure.getColumns().isEmpty() ? null : structure;
    }
    
    /**
     * 创建表
     */
    private void createTable(DataSource dataSource, String tableName, TableStructure structure) throws SQLException {
        // 简化实现：实际应该根据数据库类型生成不同的 DDL
        StringBuilder ddl = new StringBuilder("CREATE TABLE ");
        ddl.append(tableName).append(" (");
        
        for (int i = 0; i < structure.getColumns().size(); i++) {
            TableColumn column = structure.getColumns().get(i);
            if (i > 0) {
                ddl.append(", ");
            }
            ddl.append(column.getName()).append(" ").append(column.getTypeName());
            if (column.getSize() > 0) {
                ddl.append("(").append(column.getSize()).append(")");
            }
            if (!column.isNullable()) {
                ddl.append(" NOT NULL");
            }
        }
        
        // 添加主键
        if (!structure.getPrimaryKeys().isEmpty()) {
            ddl.append(", PRIMARY KEY (");
            ddl.append(String.join(", ", structure.getPrimaryKeys()));
            ddl.append(")");
        }
        
        ddl.append(")");
        
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(ddl.toString());
            log.info("创建表成功: {}", tableName);
        }
    }
    
    /**
     * 清空表
     */
    private void truncateTable(DataSource dataSource, String tableName) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE TABLE " + tableName);
            log.info("清空表成功: {}", tableName);
        }
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
     * 构建 SELECT SQL
     * TODO 对单次读取进行限制
     */
    private String buildSelectSql(String tableName, TableStructure structure) {
        String columns = structure.getColumns().stream()
                .map(TableColumn::getName)
                .collect(Collectors.joining(", "));
        return "SELECT " + columns + " FROM " + tableName;
    }
    
    /**
     * 构建 INSERT SQL
     */
    private String buildInsertSql(String tableName, TableStructure structure, List<ColumnMapping> mappings) {
        String columns = mappings.stream()
                .map(m -> m.getTargetColumn())
                .collect(Collectors.joining(", "));
        String values = mappings.stream()
                .map(m -> "?")
                .collect(Collectors.joining(", "));
        return "INSERT INTO " + tableName + " (" + columns + ") VALUES (" + values + ")";
    }
    
    /**
     * 映射列
     */
    private List<ColumnMapping> mapColumns(TableStructure source, TableStructure target) {
        List<ColumnMapping> mappings = new ArrayList<>();
        Map<String, TableColumn> targetColumnMap = target.getColumns().stream()
                .collect(Collectors.toMap(TableColumn::getName, c -> c, (a, b) -> a));
        
        for (TableColumn sourceColumn : source.getColumns()) {
            TableColumn targetColumn = targetColumnMap.get(sourceColumn.getName());
            if (targetColumn != null) {
                mappings.add(ColumnMapping.builder()
                        .sourceColumn(sourceColumn.getName())
                        .targetColumn(targetColumn.getName())
                        .sourceType(sourceColumn.getType())
                        .targetType(targetColumn.getType())
                        .build());
            }
        }
        
        return mappings;
    }
    
    /**
     * 设置 INSERT 参数
     */
    private void setInsertParameters(
            PreparedStatement stmt,
            ResultSet rs,
            List<ColumnMapping> mappings,
            TableStructure source,
            TableStructure target) throws SQLException {
        
        for (int i = 0; i < mappings.size(); i++) {
            ColumnMapping mapping = mappings.get(i);
            Object value = rs.getObject(mapping.getSourceColumn());
            stmt.setObject(i + 1, value);
        }
    }
    
    /**
     * 关闭数据源
     */
    private void closeDataSource(DataSource dataSource) {
        if (dataSource == null) {
            return;
        }
        
        try {
            // 尝试关闭 HikariDataSource
            if (dataSource.getClass().getName().equals("com.zaxxer.hikari.HikariDataSource")) {
                dataSource.getClass().getMethod("close").invoke(dataSource);
            }
        } catch (Exception e) {
            log.debug("关闭数据源失败（可能不是 HikariDataSource）", e);
        }
    }
    
    /**
     * 获取异常堆栈
     */
    private String getStackTrace(Exception e) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
    
    /**
     * 表结构
     */
    @lombok.Data
    public static class TableStructure {
        private String tableName;
        private List<TableColumn> columns;
        private List<String> primaryKeys;
    }
    
    /**
     * 表列
     */
    @lombok.Data
    public static class TableColumn {
        private String name;
        private int type;
        private String typeName;
        private int size;
        private boolean nullable;
        private String defaultValue;
    }
    
    /**
     * 列映射
     */
    @lombok.Data
    @lombok.Builder
    public static class ColumnMapping {
        private String sourceColumn;
        private String targetColumn;
        private int sourceType;
        private int targetType;
    }
}

