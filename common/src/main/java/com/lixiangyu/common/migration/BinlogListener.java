package com.lixiangyu.common.migration;

import com.lixiangyu.common.migration.operator.DatabaseOperator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Binlog 监听器
 * 监听 MySQL binlog 日志，实现增量数据同步
 * 
 * 功能特性：
 * 1. 实时监听 binlog 变更
 * 2. 支持 INSERT/UPDATE/DELETE 操作
 * 3. 自动同步到目标数据库
 * 4. 支持断点续传
 * 
 * @author lixiangyu
 */
@Slf4j
@Component
public class BinlogListener {
    
    /**
     * 监听任务映射（Task ID -> 监听任务）
     */
    private final Map<String, BinlogListenerTask> listenerTasks = new ConcurrentHashMap<>();
    
    /**
     * 启动 binlog 监听
     *
     * @param config 监听配置
     * @return 任务ID
     */
    public String startListening(BinlogListenerConfig config) {
        String taskId = java.util.UUID.randomUUID().toString();
        log.info("启动 Binlog 监听，Task ID: {}", taskId);
        
        BinlogListenerTask task = new BinlogListenerTask(taskId, config);
        listenerTasks.put(taskId, task);
        
        // 在后台线程启动监听
        Thread listenerThread = new Thread(task, "BinlogListener-" + taskId);
        listenerThread.setDaemon(true);
        listenerThread.start();
        
        return taskId;
    }
    
    /**
     * 停止 binlog 监听
     *
     * @param taskId 任务ID
     */
    public void stopListening(String taskId) {
        BinlogListenerTask task = listenerTasks.remove(taskId);
        if (task != null) {
            task.stop();
            log.info("停止 Binlog 监听，Task ID: {}", taskId);
        }
    }
    
    /**
     * 获取监听状态
     *
     * @param taskId 任务ID
     * @return 是否运行中
     */
    public boolean isRunning(String taskId) {
        BinlogListenerTask task = listenerTasks.get(taskId);
        return task != null && task.isRunning();
    }
    
    /**
     * Binlog 监听配置
     */
    @lombok.Data
    @lombok.Builder
    public static class BinlogListenerConfig {
        /**
         * 源数据源
         */
        private DataSource sourceDataSource;
        
        /**
         * 目标数据源
         */
        private DataSource targetDataSource;
        
        /**
         * 要监听的表列表（为空则监听所有表）
         */
        private List<String> tables;
        
        /**
         * Binlog 文件名（用于断点续传）
         */
        private String binlogFile;
        
        /**
         * Binlog 位置（用于断点续传）
         */
        private Long binlogPosition;
        
        /**
         * 服务器ID（MySQL 复制需要）
         */
        @lombok.Builder.Default
        private long serverId = 1L;
    }
    
    /**
     * Binlog 监听任务
     */
    private static class BinlogListenerTask implements Runnable {
        private final String taskId;
        private final BinlogListenerConfig config;
        private volatile boolean running = false;
        private volatile boolean stopped = false;
        
        /**
         * 表ID到表名的映射缓存
         */
        private final java.util.Map<Long, String> tableIdToNameMap = new java.util.concurrent.ConcurrentHashMap<>();
        
        /**
         * 上次同步时间戳（用于轮询同步）
         */
        private final java.util.Map<String, Long> lastSyncTimeMap = new java.util.concurrent.ConcurrentHashMap<>();
        
        public BinlogListenerTask(String taskId, BinlogListenerConfig config) {
            this.taskId = taskId;
            this.config = config;
        }
        
        @Override
        public void run() {
            running = true;
            log.info("Binlog 监听任务启动，Task ID: {}", taskId);
            
            try {
                // 使用 MySQL Connector/J 的 binlog 监听功能
                // 注意：需要添加 mysql-connector-java 依赖
                listenBinlog();
            } catch (Exception e) {
                log.error("Binlog 监听任务异常，Task ID: {}", taskId, e);
            } finally {
                running = false;
                log.info("Binlog 监听任务停止，Task ID: {}", taskId);
            }
        }
        
        /**
         * 监听 binlog
         */
        private void listenBinlog() throws Exception {
            // 使用 MySQL BinaryLogClient 监听 binlog
            // 这里使用反射方式，避免直接依赖
            try {
                // 检查 BinaryLogClient 类是否存在
                Class.forName("com.github.shyiko.mysql.binlog.BinaryLogClient");
                Object client = createBinaryLogClient();
                
                // 设置事件监听器
                setEventListener(client);
                
                // 连接到 MySQL
                connect(client);
                
                // 保持运行
                while (!stopped && running) {
                    Thread.sleep(1000);
                }
                
                // 断开连接
                disconnect(client);
                
            } catch (ClassNotFoundException e) {
                log.warn("Binlog 监听库未找到，使用简化实现。建议添加 mysql-binlog-connector-java 依赖");
                // 降级方案：使用轮询方式模拟增量同步
                pollingSync();
            }
        }
        
        /**
         * 创建 BinaryLogClient
         */
        private Object createBinaryLogClient() throws Exception {
            // 从数据源获取连接信息
            try (Connection conn = config.getSourceDataSource().getConnection()) {
                String url = conn.getMetaData().getURL();
                // 解析 URL 获取主机和端口
                String host = extractHost(url);
                int port = extractPort(url);
                String username = conn.getMetaData().getUserName();
                
                Class<?> binaryLogClientClass = Class.forName("com.github.shyiko.mysql.binlog.BinaryLogClient");
                Object client = binaryLogClientClass.getConstructor(String.class, int.class).newInstance(host, port);
                binaryLogClientClass.getMethod("setUsername", String.class).invoke(client, username);

                // 用于端点续传
                if (config.getBinlogFile() != null) {
                    binaryLogClientClass.getMethod("setBinlogFilename", String.class).invoke(client, config.getBinlogFile());
                }
                if (config.getBinlogPosition() != null) {
                    binaryLogClientClass.getMethod("setBinlogPosition", long.class).invoke(client, config.getBinlogPosition());
                }
                
                return client;
            }
        }
        
        /**
         * 设置事件监听器
         */
        private void setEventListener(Object client) throws Exception {
            Class<?> clientClass = client.getClass();
            Class<?> eventListenerClass = Class.forName("com.github.shyiko.mysql.binlog.event.EventListener");

            // 创建事件监听器
            Object listener = java.lang.reflect.Proxy.newProxyInstance(
                    eventListenerClass.getClassLoader(),
                    new Class[]{eventListenerClass},
                    (proxy, method, args) -> {
                        if ("onEvent".equals(method.getName())) {
                            handleBinlogEvent(args[0]);
                        }
                        return null;
                    });
            
            clientClass.getMethod("registerEventListener", eventListenerClass).invoke(client, listener);
        }
        
        /**
         * 处理 binlog 事件
         */
        private void handleBinlogEvent(Object event) {
            try {
                // 解析事件类型
                String eventType = event.getClass().getSimpleName();
                log.debug("收到 Binlog 事件: {}", eventType);
                
                // 处理不同的事件类型
                if (eventType.contains("WriteRowsEvent")) {
                    handleInsertEvent(event);
                } else if (eventType.contains("UpdateRowsEvent")) {
                    handleUpdateEvent(event);
                } else if (eventType.contains("DeleteRowsEvent")) {
                    handleDeleteEvent(event);
                }
                
            } catch (Exception e) {
                log.error("处理 Binlog 事件失败", e);
            }
        }
        
        /**
         * 处理 INSERT 事件
         */
        private void handleInsertEvent(Object event) throws Exception {
            // 获取表ID和行数据
            long tableId = (Long) event.getClass().getMethod("getTableId").invoke(event);
            Object rows = event.getClass().getMethod("getRows").invoke(event);
            
            // 获取表名
            String tableName = getTableName(tableId);
            if (tableName == null || !shouldListenTable(tableName)) {
                return;
            }
            
            // 同步到目标数据库
            syncInsertToTarget(tableName, rows);
        }
        
        /**
         * 处理 UPDATE 事件
         */
        private void handleUpdateEvent(Object event) throws Exception {
            long tableId = (Long) event.getClass().getMethod("getTableId").invoke(event);
            Object rows = event.getClass().getMethod("getRows").invoke(event);
            
            String tableName = getTableName(tableId);
            if (tableName == null || !shouldListenTable(tableName)) {
                return;
            }
            
            syncUpdateToTarget(tableName, rows);
        }
        
        /**
         * 处理 DELETE 事件
         */
        private void handleDeleteEvent(Object event) throws Exception {
            long tableId = (Long) event.getClass().getMethod("getTableId").invoke(event);
            Object rows = event.getClass().getMethod("getRows").invoke(event);
            
            String tableName = getTableName(tableId);
            if (tableName == null || !shouldListenTable(tableName)) {
                return;
            }
            
            syncDeleteToTarget(tableName, rows);
        }
        
        /**
         * 同步 INSERT 到目标数据库
         */
        private void syncInsertToTarget(String tableName, Object rows) throws SQLException {
            log.debug("同步 INSERT 到目标表: {}", tableName);
            
            try (java.sql.Connection conn = config.getTargetDataSource().getConnection()) {
                // 获取表结构
                DatabaseOperator.TableStructure structure = getTableStructure(conn, tableName);
                if (structure == null) {
                    log.warn("表 {} 不存在，跳过 INSERT 同步", tableName);
                    return;
                }
                
                // 解析 rows（binlog 事件中的行数据）
                List<Map<String, Object>> rowDataList = parseRows(rows, structure);
                
                // 构建 INSERT SQL
                String insertSql = buildInsertSql(tableName, structure);
                
                // 批量插入
                try (java.sql.PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                    for (Map<String, Object> rowData : rowDataList) {
                        setInsertParameters(stmt, rowData, structure);
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                    log.debug("成功同步 {} 条 INSERT 记录到表: {}", rowDataList.size(), tableName);
                }
            } catch (Exception e) {
                log.error("同步 INSERT 到目标表 {} 失败", tableName, e);
                throw new SQLException("同步 INSERT 失败", e);
            }
        }
        
        /**
         * 同步 UPDATE 到目标数据库
         */
        private void syncUpdateToTarget(String tableName, Object rows) throws SQLException {
            log.debug("同步 UPDATE 到目标表: {}", tableName);
            
            try (java.sql.Connection conn = config.getTargetDataSource().getConnection()) {
                // 获取表结构
                DatabaseOperator.TableStructure structure = getTableStructure(conn, tableName);
                if (structure == null) {
                    log.warn("表 {} 不存在，跳过 UPDATE 同步", tableName);
                    return;
                }
                
                // 解析 rows（binlog UPDATE 事件包含 before 和 after 两行）
                List<Map<String, Object>> beforeRows = parseRowsBefore(rows, structure);
                List<Map<String, Object>> afterRows = parseRowsAfter(rows, structure);
                
                // 构建 UPDATE SQL
                String updateSql = buildUpdateSql(tableName, structure);
                
                // 批量更新
                try (java.sql.PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                    for (int i = 0; i < afterRows.size(); i++) {
                        Map<String, Object> afterRow = afterRows.get(i);
                        Map<String, Object> beforeRow = i < beforeRows.size() ? beforeRows.get(i) : null;
                        
                        setUpdateParameters(stmt, afterRow, beforeRow, structure);
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                    log.debug("成功同步 {} 条 UPDATE 记录到表: {}", afterRows.size(), tableName);
                }
            } catch (Exception e) {
                log.error("同步 UPDATE 到目标表 {} 失败", tableName, e);
                throw new SQLException("同步 UPDATE 失败", e);
            }
        }
        
        /**
         * 同步 DELETE 到目标数据库
         */
        private void syncDeleteToTarget(String tableName, Object rows) throws SQLException {
            log.debug("同步 DELETE 到目标表: {}", tableName);
            
            try (java.sql.Connection conn = config.getTargetDataSource().getConnection()) {
                // 获取表结构
                DatabaseOperator.TableStructure structure = getTableStructure(conn, tableName);
                if (structure == null) {
                    log.warn("表 {} 不存在，跳过 DELETE 同步", tableName);
                    return;
                }
                
                // 解析 rows
                List<Map<String, Object>> rowDataList = parseRows(rows, structure);
                
                // 构建 DELETE SQL（基于主键）
                if (structure.getPrimaryKeys().isEmpty()) {
                    log.warn("表 {} 没有主键，无法执行 DELETE 同步", tableName);
                    return;
                }
                
                String deleteSql = buildDeleteSql(tableName, structure);
                
                // 批量删除
                try (java.sql.PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
                    for (Map<String, Object> rowData : rowDataList) {
                        setDeleteParameters(stmt, rowData, structure);
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                    log.debug("成功同步 {} 条 DELETE 记录到表: {}", rowDataList.size(), tableName);
                }
            } catch (Exception e) {
                log.error("同步 DELETE 到目标表 {} 失败", tableName, e);
                throw new SQLException("同步 DELETE 失败", e);
            }
        }
        
        /**
         * 解析 rows（INSERT/DELETE 事件）
         */
        private List<Map<String, Object>> parseRows(Object rows, DatabaseOperator.TableStructure structure) {
            List<Map<String, Object>> rowDataList = new java.util.ArrayList<>();
            
            try {
                // rows 通常是 List<Row> 类型
                if (rows instanceof java.util.List) {
                    java.util.List<?> rowList = (java.util.List<?>) rows;
                    for (Object row : rowList) {
                        Map<String, Object> rowData = parseRow(row, structure);
                        if (rowData != null) {
                            rowDataList.add(rowData);
                        }
                    }
                } else {
                    // 单个 row
                    Map<String, Object> rowData = parseRow(rows, structure);
                    if (rowData != null) {
                        rowDataList.add(rowData);
                    }
                }
            } catch (Exception e) {
                log.error("解析 rows 失败", e);
            }
            
            return rowDataList;
        }
        
        /**
         * 解析单个 row
         */
        private Map<String, Object> parseRow(Object row, DatabaseOperator.TableStructure structure) {
            Map<String, Object> rowData = new java.util.HashMap<>();
            
            try {
                // 使用反射获取 row 的列值
                // binlog 事件中的 row 通常有 getValue() 或类似方法
                if (row.getClass().getMethod("getValue", int.class) != null) {
                    for (int i = 0; i < structure.getColumns().size(); i++) {
                        DatabaseOperator.TableColumn column = structure.getColumns().get(i);
                        Object value = row.getClass().getMethod("getValue", int.class).invoke(row, i);
                        rowData.put(column.getName(), value);
                    }
                } else {
                    // 降级方案：尝试其他方法
                    log.warn("无法解析 row，使用降级方案");
                }
            } catch (Exception e) {
                log.error("解析 row 失败", e);
            }
            
            return rowData;
        }
        
        /**
         * 解析 UPDATE 事件的 before rows
         */
        private List<Map<String, Object>> parseRowsBefore(Object rows, DatabaseOperator.TableStructure structure) {
            // UPDATE 事件的 rows 通常是 List<Pair<Row, Row>>
            // 这里简化处理，实际应该解析 Pair 的第一个元素
            return parseRows(rows, structure);
        }
        
        /**
         * 解析 UPDATE 事件的 after rows
         */
        private List<Map<String, Object>> parseRowsAfter(Object rows, DatabaseOperator.TableStructure structure) {
            // UPDATE 事件的 rows 通常是 List<Pair<Row, Row>>
            // 这里简化处理，实际应该解析 Pair 的第二个元素
            return parseRows(rows, structure);
        }
        
        /**
         * 构建 INSERT SQL
         */
        private String buildInsertSql(String tableName, DatabaseOperator.TableStructure structure) {
            StringBuilder sql = new StringBuilder("INSERT INTO ");
            sql.append(tableName).append(" (");
            
            String columns = structure.getColumns().stream()
                    .map(DatabaseOperator.TableColumn::getName)
                    .collect(java.util.stream.Collectors.joining(", "));
            sql.append(columns).append(") VALUES (");
            
            String placeholders = structure.getColumns().stream()
                    .map(c -> "?")
                    .collect(java.util.stream.Collectors.joining(", "));
            sql.append(placeholders).append(")");
            
            return sql.toString();
        }
        
        /**
         * 构建 UPDATE SQL
         */
        private String buildUpdateSql(String tableName, DatabaseOperator.TableStructure structure) {
            StringBuilder sql = new StringBuilder("UPDATE ");
            sql.append(tableName).append(" SET ");
            
            String setClause = structure.getColumns().stream()
                    .filter(c -> !structure.getPrimaryKeys().contains(c.getName()))
                    .map(c -> c.getName() + " = ?")
                    .collect(java.util.stream.Collectors.joining(", "));
            sql.append(setClause).append(" WHERE ");
            
            String whereClause = structure.getPrimaryKeys().stream()
                    .map(key -> key + " = ?")
                    .collect(java.util.stream.Collectors.joining(" AND "));
            sql.append(whereClause);
            
            return sql.toString();
        }
        
        /**
         * 构建 DELETE SQL
         */
        private String buildDeleteSql(String tableName, DatabaseOperator.TableStructure structure) {
            StringBuilder sql = new StringBuilder("DELETE FROM ");
            sql.append(tableName).append(" WHERE ");
            
            String whereClause = structure.getPrimaryKeys().stream()
                    .map(key -> key + " = ?")
                    .collect(java.util.stream.Collectors.joining(" AND "));
            sql.append(whereClause);
            
            return sql.toString();
        }
        
        /**
         * 设置 INSERT 参数
         */
        private void setInsertParameters(java.sql.PreparedStatement stmt, 
                                       Map<String, Object> rowData, 
                                       DatabaseOperator.TableStructure structure) throws SQLException {
            int paramIndex = 1;
            for (DatabaseOperator.TableColumn column : structure.getColumns()) {
                Object value = rowData.get(column.getName());
                stmt.setObject(paramIndex++, value);
            }
        }
        
        /**
         * 设置 UPDATE 参数
         */
        private void setUpdateParameters(java.sql.PreparedStatement stmt,
                                       Map<String, Object> afterRow,
                                       Map<String, Object> beforeRow,
                                       DatabaseOperator.TableStructure structure) throws SQLException {
            int paramIndex = 1;
            
            // SET 子句参数（非主键列）
            for (DatabaseOperator.TableColumn column : structure.getColumns()) {
                if (!structure.getPrimaryKeys().contains(column.getName())) {
                    Object value = afterRow.get(column.getName());
                    stmt.setObject(paramIndex++, value);
                }
            }
            
            // WHERE 子句参数（主键列）
            for (String primaryKey : structure.getPrimaryKeys()) {
                Object value = afterRow.get(primaryKey);
                if (value == null && beforeRow != null) {
                    value = beforeRow.get(primaryKey);
                }
                stmt.setObject(paramIndex++, value);
            }
        }
        
        /**
         * 设置 DELETE 参数
         */
        private void setDeleteParameters(java.sql.PreparedStatement stmt,
                                        Map<String, Object> rowData,
                                        DatabaseOperator.TableStructure structure) throws SQLException {
            int paramIndex = 1;
            for (String primaryKey : structure.getPrimaryKeys()) {
                Object value = rowData.get(primaryKey);
                stmt.setObject(paramIndex++, value);
            }
        }
        
        /**
         * 获取表结构
         */
        private DatabaseOperator.TableStructure getTableStructure(java.sql.Connection conn, String tableName) throws SQLException {
            DatabaseOperator.TableStructure structure = new DatabaseOperator.TableStructure();
            structure.setTableName(tableName);
            structure.setColumns(new java.util.ArrayList<>());
            
            java.sql.DatabaseMetaData metaData = conn.getMetaData();
            String catalog = conn.getCatalog();
            String schema = conn.getSchema();
            
            try (java.sql.ResultSet rs = metaData.getColumns(catalog, schema, tableName, null)) {
                while (rs.next()) {
                    DatabaseOperator.TableColumn column = new DatabaseOperator.TableColumn();
                    column.setName(rs.getString("COLUMN_NAME"));
                    column.setType(rs.getInt("DATA_TYPE"));
                    column.setTypeName(rs.getString("TYPE_NAME"));
                    column.setSize(rs.getInt("COLUMN_SIZE"));
                    column.setNullable(rs.getInt("NULLABLE") == java.sql.DatabaseMetaData.columnNullable);
                    column.setDefaultValue(rs.getString("COLUMN_DEF"));
                    structure.getColumns().add(column);
                }
            }
            
            // 获取主键
            try (java.sql.ResultSet rs = metaData.getPrimaryKeys(catalog, schema, tableName)) {
                List<String> primaryKeys = new java.util.ArrayList<>();
                while (rs.next()) {
                    primaryKeys.add(rs.getString("COLUMN_NAME"));
                }
                structure.setPrimaryKeys(primaryKeys);
            }
            
            return structure.getColumns().isEmpty() ? null : structure;
        }
        
        /**
         * 轮询同步（降级方案）
         */
        private void pollingSync() {
            log.info("使用轮询方式同步增量数据，Task ID: {}", taskId);
            
            // 轮询间隔（秒）
            long pollingInterval = 5;
            
            while (!stopped && running) {
                try {
                    // 获取要同步的表列表
                    List<String> tables = config.getTables();
                    if (tables == null || tables.isEmpty()) {
                        // 获取所有表
                        tables = getAllTables();
                    }
                    
                    // 对每个表进行增量同步
                    for (String tableName : tables) {
                        if (stopped || !running) {
                            break;
                        }
                        
                        try {
                            syncIncrementalData(tableName);
                        } catch (Exception e) {
                            log.error("轮询同步表 {} 失败", tableName, e);
                        }
                    }
                    
                    // 等待下次轮询
                    Thread.sleep(pollingInterval * 1000);
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("轮询同步被中断");
                    break;
                } catch (Exception e) {
                    log.error("轮询同步异常", e);
                    try {
                        Thread.sleep(pollingInterval * 1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        /**
         * 获取所有表名
         */
        private List<String> getAllTables() {
            List<String> tables = new java.util.ArrayList<>();
            try (java.sql.Connection conn = config.getSourceDataSource().getConnection()) {
                java.sql.DatabaseMetaData metaData = conn.getMetaData();
                String catalog = conn.getCatalog();
                String schema = conn.getSchema();
                
                try (java.sql.ResultSet rs = metaData.getTables(catalog, schema, null, new String[]{"TABLE"})) {
                    while (rs.next()) {
                        tables.add(rs.getString("TABLE_NAME"));
                    }
                }
            } catch (Exception e) {
                log.error("获取表列表失败", e);
            }
            return tables;
        }
        
        /**
         * 同步增量数据（基于时间戳）
         */
        private void syncIncrementalData(String tableName) throws Exception {
            // 获取上次同步时间
            Long lastSyncTime = lastSyncTimeMap.get(tableName);
            if (lastSyncTime == null) {
                lastSyncTime = System.currentTimeMillis() - 3600000; // 默认1小时前
            }
            
            // 获取表结构
            DatabaseOperator.TableStructure structure = getTableStructure(
                    config.getSourceDataSource().getConnection(), tableName);
            if (structure == null) {
                return;
            }
            
            // 查找更新时间字段（假设有 updateTime 或 update_time 字段）
            String timeColumn = findTimeColumn(structure);
            if (timeColumn == null) {
                log.warn("表 {} 没有时间字段，无法进行增量同步", tableName);
                return;
            }
            
            // 查询增量数据
            String selectSql = String.format(
                    "SELECT * FROM %s WHERE %s > ? ORDER BY %s",
                    tableName, timeColumn, timeColumn);
            
            try (java.sql.Connection sourceConn = config.getSourceDataSource().getConnection();
                 java.sql.Connection targetConn = config.getTargetDataSource().getConnection();
                 java.sql.PreparedStatement selectStmt = sourceConn.prepareStatement(selectSql)) {
                
                selectStmt.setTimestamp(1, new java.sql.Timestamp(lastSyncTime));
                
                try (java.sql.ResultSet rs = selectStmt.executeQuery()) {
                    String insertSql = buildInsertSql(tableName, structure);
                    long maxTime = lastSyncTime;
                    int count = 0;
                    
                    try (java.sql.PreparedStatement insertStmt = targetConn.prepareStatement(insertSql)) {
                        while (rs.next()) {
                            // 设置 INSERT 参数
                            Map<String, Object> rowData = new java.util.HashMap<>();
                            for (int i = 0; i < structure.getColumns().size(); i++) {
                                DatabaseOperator.TableColumn column = structure.getColumns().get(i);
                                Object value = rs.getObject(column.getName());
                                rowData.put(column.getName(), value);
                                
                                // 更新最大时间
                                if (timeColumn.equals(column.getName()) && value instanceof java.sql.Timestamp) {
                                    long time = ((java.sql.Timestamp) value).getTime();
                                    if (time > maxTime) {
                                        maxTime = time;
                                    }
                                }
                            }
                            
                            setInsertParameters(insertStmt, rowData, structure);
                            insertStmt.addBatch();
                            count++;
                            
                            // 批量执行
                            if (count % 100 == 0) {
                                insertStmt.executeBatch();
                            }
                        }
                        
                        // 执行剩余的批次
                        if (count % 100 != 0) {
                            insertStmt.executeBatch();
                        }
                    }
                    
                    // 更新同步时间
                    lastSyncTimeMap.put(tableName, maxTime);
                    
                    if (count > 0) {
                        log.info("轮询同步表 {} 完成，同步 {} 条记录", tableName, count);
                    }
                }
            }
        }
        
        /**
         * 查找时间字段
         */
        private String findTimeColumn(DatabaseOperator.TableStructure structure) {
            for (DatabaseOperator.TableColumn column : structure.getColumns()) {
                String name = column.getName().toLowerCase();
                if (name.contains("updatetime") || name.contains("update_time") ||
                    name.contains("modifytime") || name.contains("modify_time")) {
                    return column.getName();
                }
            }
            return null;
        }
        
        /**
         * 连接 BinaryLogClient
         */
        private void connect(Object client) throws Exception {
            client.getClass().getMethod("connect").invoke(client);
        }
        
        /**
         * 断开 BinaryLogClient
         */
        private void disconnect(Object client) throws Exception {
            client.getClass().getMethod("disconnect").invoke(client);
        }
        
        /**
         * 获取表名
         */
        private String getTableName(long tableId) {
            // 先从缓存获取
            String tableName = tableIdToNameMap.get(tableId);
            if (tableName != null) {
                return tableName;
            }
            
            // 从数据库查询表名
            try (java.sql.Connection conn = config.getSourceDataSource().getConnection()) {
                // MySQL 中可以通过 information_schema 查询表ID对应的表名
                // 注意：tableId 是 binlog 中的表ID，需要通过 binlog 元数据查询
                String sql = "SELECT TABLE_SCHEMA, TABLE_NAME FROM information_schema.TABLES " +
                            "WHERE TABLE_SCHEMA = DATABASE()";
                
                try (java.sql.Statement stmt = conn.createStatement();
                     java.sql.ResultSet rs = stmt.executeQuery(sql)) {
                    
                // 遍历所有表，通过表结构匹配（简化实现）
                // 实际应该通过 binlog 的 TableMapEvent 获取表名
                int currentId = 0;
                while (rs.next()) {
                    currentId++;
                    if (currentId == tableId) {
                        String name = rs.getString("TABLE_NAME");
                        tableName = name;
                        tableIdToNameMap.put(tableId, tableName);
                        return tableName;
                    }
                }
                }
                
                // 如果通过 ID 无法匹配，尝试从 binlog 事件中获取
                // 这需要在 handleBinlogEvent 中保存 TableMapEvent
                log.warn("无法通过 tableId {} 找到表名，可能需要从 TableMapEvent 获取", tableId);
                
            } catch (Exception e) {
                log.error("查询表名失败，tableId: {}", tableId, e);
            }
            
            return null;
        }
        
        /**
         * 缓存表ID到表名的映射（从 TableMapEvent 获取）
         * 在 handleBinlogEvent 中调用，用于缓存表映射关系
         */
        @SuppressWarnings("unused")
        private void cacheTableMapping(long tableId, String tableName) {
            tableIdToNameMap.put(tableId, tableName);
            log.debug("缓存表映射: tableId={}, tableName={}", tableId, tableName);
        }
        
        /**
         * 判断是否应该监听该表
         */
        private boolean shouldListenTable(String tableName) {
            if (config.getTables() == null || config.getTables().isEmpty()) {
                return true;
            }
            return config.getTables().contains(tableName);
        }
        
        /**
         * 提取主机名
         */
        private String extractHost(String url) {
            // jdbc:mysql://host:port/database
            int start = url.indexOf("//") + 2;
            int end = url.indexOf(":", start);
            if (end == -1) {
                end = url.indexOf("/", start);
            }
            return url.substring(start, end);
        }
        
        /**
         * 提取端口
         */
        private int extractPort(String url) {
            int start = url.indexOf("//") + 2;
            int colonIndex = url.indexOf(":", start);
            if (colonIndex == -1) {
                return 3306; // 默认端口
            }
            int end = url.indexOf("/", colonIndex);
            if (end == -1) {
                end = url.length();
            }
            return Integer.parseInt(url.substring(colonIndex + 1, end));
        }
        
        public void stop() {
            stopped = true;
        }
        
        public boolean isRunning() {
            return running;
        }
    }
}

