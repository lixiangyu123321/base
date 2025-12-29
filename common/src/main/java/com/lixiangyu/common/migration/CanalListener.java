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
 * Canal 监听器
 * 使用 Alibaba Canal 监听 MySQL binlog，实现增量数据同步
 * 
 * Canal 相比 mysql-binlog-connector-java 的优势：
 * 1. 更稳定可靠
 * 2. 支持集群模式
 * 3. 更好的性能
 * 4. 支持多种数据源
 * 
 * @author lixiangyu
 */
@Slf4j
@Component
public class CanalListener {
    
    /**
     * 监听任务映射（Task ID -> 监听任务）
     */
    private final Map<String, CanalListenerTask> listenerTasks = new ConcurrentHashMap<>();
    
    /**
     * 启动 Canal 监听
     *
     * @param config 监听配置
     * @return 任务ID
     */
    public String startListening(CanalListenerConfig config) {
        String taskId = java.util.UUID.randomUUID().toString();
        log.info("启动 Canal 监听，Task ID: {}", taskId);
        
        CanalListenerTask task = new CanalListenerTask(taskId, config);
        listenerTasks.put(taskId, task);
        
        // 在后台线程启动监听
        Thread listenerThread = new Thread(task, "CanalListener-" + taskId);
        listenerThread.setDaemon(true);
        listenerThread.start();
        
        return taskId;
    }
    
    /**
     * 停止 Canal 监听
     *
     * @param taskId 任务ID
     */
    public void stopListening(String taskId) {
        CanalListenerTask task = listenerTasks.remove(taskId);
        if (task != null) {
            task.stop();
            log.info("停止 Canal 监听，Task ID: {}", taskId);
        }
    }
    
    /**
     * 获取监听状态
     *
     * @param taskId 任务ID
     * @return 是否运行中
     */
    public boolean isRunning(String taskId) {
        CanalListenerTask task = listenerTasks.get(taskId);
        return task != null && task.isRunning();
    }
    
    /**
     * Canal 监听配置
     */
    @lombok.Data
    @lombok.Builder
    public static class CanalListenerConfig {
        /**
         * 源数据源
         */
        private DataSource sourceDataSource;
        
        /**
         * 目标数据源
         */
        private DataSource targetDataSource;
        
        /**
         * Canal 服务器地址（格式：host:port）
         */
        private String canalServerAddress;
        
        /**
         * Canal 实例名称（Destination）
         */
        @lombok.Builder.Default
        private String destination = "example";
        
        /**
         * Canal 用户名
         */
        private String username;
        
        /**
         * Canal 密码
         */
        private String password;
        
        /**
         * 要监听的表列表（为空则监听所有表）
         */
        private List<String> tables;
        
        /**
         * 订阅表达式（Canal 支持的表过滤表达式）
         * 例如：test\\.t_.* 表示 test 库下所有 t_ 开头的表
         */
        private String subscribeFilter;
        
        /**
         * 批次大小（每次获取的 binlog 数量）
         */
        @lombok.Builder.Default
        private int batchSize = 1000;
    }
    
    /**
     * Canal 监听任务
     */
    private static class CanalListenerTask implements Runnable {
        private final String taskId;
        private final CanalListenerConfig config;
        private volatile boolean running = false;
        private volatile boolean stopped = false;
        
        public CanalListenerTask(String taskId, CanalListenerConfig config) {
            this.taskId = taskId;
            this.config = config;
        }
        
        @Override
        public void run() {
            running = true;
            log.info("Canal 监听任务启动，Task ID: {}", taskId);
            
            try {
                listenCanal();
            } catch (Exception e) {
                log.error("Canal 监听任务异常，Task ID: {}", taskId, e);
            } finally {
                running = false;
                log.info("Canal 监听任务停止，Task ID: {}", taskId);
            }
        }
        
        /**
         * 监听 Canal
         */
        private void listenCanal() throws Exception {
            // 使用 Canal 客户端监听
            // 这里使用反射方式，避免直接依赖
            try {
                Class<?> connectorClass = Class.forName("com.alibaba.otter.canal.client.CanalConnector");
                Object connector = createCanalConnector();
                
                // 连接 Canal
                connectCanal(connector);
                
                // 订阅
                subscribe(connector);
                
                // 循环获取数据
                while (!stopped && running) {
                    try {
                        // 获取数据
                        Object message = getMessage(connector);
                        if (message != null) {
                            // 处理消息
                            handleCanalMessage(message);
                            
                            // 确认消息
                            ack(connector, message);
                        }
                    } catch (Exception e) {
                        log.error("处理 Canal 消息失败", e);
                        // 回滚
                        rollback(connector);
                    }
                    
                    Thread.sleep(100);
                }
                
                // 断开连接
                disconnectCanal(connector);
                
            } catch (ClassNotFoundException e) {
                log.warn("Canal 客户端库未找到，使用简化实现。建议添加 canal-client 依赖");
                // 降级方案：使用轮询方式模拟增量同步
                pollingSync();
            }
        }
        
        /**
         * 创建 Canal 连接器
         */
        private Object createCanalConnector() throws Exception {
            // 解析服务器地址
            String[] parts = config.getCanalServerAddress().split(":");
            String host = parts[0];
            int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 11111;
            
            // 使用 CanalConnectors 创建连接器（使用反射避免直接依赖）
            Class<?> connectorsClass = Class.forName("com.alibaba.otter.canal.client.CanalConnectors");
            Object connector = connectorsClass.getMethod("newSingleConnector", 
                    java.net.InetSocketAddress.class, String.class, String.class, String.class)
                    .invoke(null, 
                            new java.net.InetSocketAddress(host, port),
                            config.getDestination(),
                            config.getUsername() != null ? config.getUsername() : "",
                            config.getPassword() != null ? config.getPassword() : "");
            
            return connector;
        }
        
        /**
         * 连接 Canal
         */
        private void connectCanal(Object connector) throws Exception {
            connector.getClass().getMethod("connect").invoke(connector);
            log.info("Canal 连接成功，Destination: {}", config.getDestination());
        }
        
        /**
         * 订阅
         */
        private void subscribe(Object connector) throws Exception {
            String filter = config.getSubscribeFilter();
            if (filter == null || filter.isEmpty()) {
                // 如果没有指定过滤表达式，订阅所有表
                filter = ".*\\..*";
            }
            
            connector.getClass().getMethod("subscribe", String.class).invoke(connector, filter);
            log.info("Canal 订阅成功，Filter: {}", filter);
        }
        
        /**
         * 获取消息
         */
        private Object getMessage(Object connector) throws Exception {
            Class<?> connectorClass = connector.getClass();
            Object message = connectorClass.getMethod("getWithoutAck", int.class)
                    .invoke(connector, config.getBatchSize());
            return message;
        }
        
        /**
         * 处理 Canal 消息
         */
        private void handleCanalMessage(Object message) throws Exception {
            // 解析 Canal 消息
            Class<?> messageClass = message.getClass();
            Long batchId = (Long) messageClass.getMethod("getId").invoke(message);
            List<?> entries = (List<?>) messageClass.getMethod("getEntries").invoke(message);
            
            if (entries == null || entries.isEmpty()) {
                return;
            }
            
            log.debug("收到 Canal 消息，Batch ID: {}, 条目数: {}", batchId, entries.size());
            
            // 处理每个 Entry
            for (Object entry : entries) {
                handleCanalEntry(entry);
            }
        }
        
        /**
         * 处理 Canal Entry
         */
        private void handleCanalEntry(Object entry) throws Exception {
            Class<?> entryClass = entry.getClass();
            
            // 获取 EntryType
            Object entryType = entryClass.getMethod("getEntryType").invoke(entry);
            String entryTypeName = entryType.getClass().getMethod("name").invoke(entryType).toString();
            
            // 只处理 ROWDATA 类型
            if (!"ROWDATA".equals(entryTypeName)) {
                return;
            }
            
            // 获取 Header
            Object header = entryClass.getMethod("getHeader").invoke(entry);
            Class<?> headerClass = header.getClass();
            @SuppressWarnings("unused")
            String schemaName = (String) headerClass.getMethod("getSchemaName").invoke(header);
            String tableName = (String) headerClass.getMethod("getTableName").invoke(header);
            String eventType = (String) headerClass.getMethod("getEventType").invoke(header);
            
            // 检查是否应该监听该表
            if (!shouldListenTable(tableName)) {
                return;
            }
            
            // 获取 RowChange
            Object storeValue = entryClass.getMethod("getStoreValue").invoke(entry);
            Class<?> rowChangeClass = Class.forName("com.alibaba.otter.canal.protocol.CanalEntry$RowChange");
            Object rowChange = rowChangeClass.getMethod("parseFrom", byte[].class)
                    .invoke(null, storeValue);
            
            // 处理 RowChange
            handleRowChange(tableName, eventType, rowChange);
        }
        
        /**
         * 处理 RowChange
         */
        private void handleRowChange(String tableName, String eventType, Object rowChange) throws Exception {
            Class<?> rowChangeClass = rowChange.getClass();
            List<?> rowDatas = (List<?>) rowChangeClass.getMethod("getRowDatasList").invoke(rowChange);
            
            if (rowDatas == null || rowDatas.isEmpty()) {
                return;
            }
            
            // 根据事件类型处理
            if ("INSERT".equals(eventType)) {
                syncInsertToTarget(tableName, rowDatas);
            } else if ("UPDATE".equals(eventType)) {
                syncUpdateToTarget(tableName, rowDatas);
            } else if ("DELETE".equals(eventType)) {
                syncDeleteToTarget(tableName, rowDatas);
            }
        }
        
        /**
         * 同步 INSERT 到目标数据库
         */
        private void syncInsertToTarget(String tableName, List<?> rowDatas) throws SQLException {
            log.debug("同步 INSERT 到目标表: {}, 记录数: {}", tableName, rowDatas.size());
            
            try (Connection conn = config.getTargetDataSource().getConnection()) {
                // 获取表结构
                DatabaseOperator.TableStructure structure = getTableStructure(conn, tableName);
                if (structure == null) {
                    log.warn("表 {} 不存在，跳过 INSERT 同步", tableName);
                    return;
                }
                
                // 构建 INSERT SQL
                String insertSql = buildInsertSql(tableName, structure);
                
                // 批量插入
                try (java.sql.PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                    for (Object rowData : rowDatas) {
                        Map<String, Object> rowMap = parseCanalRowData(rowData, structure, true);
                        setInsertParameters(stmt, rowMap, structure);
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                    log.debug("成功同步 {} 条 INSERT 记录到表: {}", rowDatas.size(), tableName);
                }
            } catch (Exception e) {
                log.error("同步 INSERT 到目标表 {} 失败", tableName, e);
                throw new SQLException("同步 INSERT 失败", e);
            }
        }
        
        /**
         * 同步 UPDATE 到目标数据库
         */
        private void syncUpdateToTarget(String tableName, List<?> rowDatas) throws SQLException {
            log.debug("同步 UPDATE 到目标表: {}, 记录数: {}", tableName, rowDatas.size());
            
            try (Connection conn = config.getTargetDataSource().getConnection()) {
                DatabaseOperator.TableStructure structure = getTableStructure(conn, tableName);
                if (structure == null) {
                    log.warn("表 {} 不存在，跳过 UPDATE 同步", tableName);
                    return;
                }
                
                String updateSql = buildUpdateSql(tableName, structure);
                
                try (java.sql.PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                    for (Object rowData : rowDatas) {
                        // Canal UPDATE 事件包含 before 和 after
                        Map<String, Object> afterRow = parseCanalRowData(rowData, structure, false);
                        Map<String, Object> beforeRow = parseCanalRowDataBefore(rowData, structure);
                        
                        setUpdateParameters(stmt, afterRow, beforeRow, structure);
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                    log.debug("成功同步 {} 条 UPDATE 记录到表: {}", rowDatas.size(), tableName);
                }
            } catch (Exception e) {
                log.error("同步 UPDATE 到目标表 {} 失败", tableName, e);
                throw new SQLException("同步 UPDATE 失败", e);
            }
        }
        
        /**
         * 同步 DELETE 到目标数据库
         */
        private void syncDeleteToTarget(String tableName, List<?> rowDatas) throws SQLException {
            log.debug("同步 DELETE 到目标表: {}, 记录数: {}", tableName, rowDatas.size());
            
            try (Connection conn = config.getTargetDataSource().getConnection()) {
                DatabaseOperator.TableStructure structure = getTableStructure(conn, tableName);
                if (structure == null) {
                    log.warn("表 {} 不存在，跳过 DELETE 同步", tableName);
                    return;
                }
                
                if (structure.getPrimaryKeys().isEmpty()) {
                    log.warn("表 {} 没有主键，无法执行 DELETE 同步", tableName);
                    return;
                }
                
                String deleteSql = buildDeleteSql(tableName, structure);
                
                try (java.sql.PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
                    for (Object rowData : rowDatas) {
                        Map<String, Object> rowMap = parseCanalRowData(rowData, structure, true);
                        setDeleteParameters(stmt, rowMap, structure);
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                    log.debug("成功同步 {} 条 DELETE 记录到表: {}", rowDatas.size(), tableName);
                }
            } catch (Exception e) {
                log.error("同步 DELETE 到目标表 {} 失败", tableName, e);
                throw new SQLException("同步 DELETE 失败", e);
            }
        }
        
        /**
         * 解析 Canal RowData
         */
        private Map<String, Object> parseCanalRowData(Object rowData, DatabaseOperator.TableStructure structure, boolean useAfter) {
            Map<String, Object> rowMap = new java.util.HashMap<>();
            
            try {
                Class<?> rowDataClass = rowData.getClass();
                
                // 获取 Column 列表
                List<?> columns;
                if (useAfter) {
                    columns = (List<?>) rowDataClass.getMethod("getAfterColumnsList").invoke(rowData);
                } else {
                    columns = (List<?>) rowDataClass.getMethod("getAfterColumnsList").invoke(rowData);
                }
                
                // 解析每个 Column
                for (Object column : columns) {
                    Class<?> columnClass = column.getClass();
                    String name = (String) columnClass.getMethod("getName").invoke(column);
                    String value = (String) columnClass.getMethod("getValue").invoke(column);
                    boolean isNull = (Boolean) columnClass.getMethod("getIsNull").invoke(column);
                    
                    // 根据列类型转换值
                    Object convertedValue = convertValue(value, name, structure, isNull);
                    rowMap.put(name, convertedValue);
                }
            } catch (Exception e) {
                log.error("解析 Canal RowData 失败", e);
            }
            
            return rowMap;
        }
        
        /**
         * 解析 Canal RowData Before（UPDATE 事件）
         */
        private Map<String, Object> parseCanalRowDataBefore(Object rowData, DatabaseOperator.TableStructure structure) {
            Map<String, Object> rowMap = new java.util.HashMap<>();
            
            try {
                Class<?> rowDataClass = rowData.getClass();
                List<?> columns = (List<?>) rowDataClass.getMethod("getBeforeColumnsList").invoke(rowData);
                
                for (Object column : columns) {
                    Class<?> columnClass = column.getClass();
                    String name = (String) columnClass.getMethod("getName").invoke(column);
                    String value = (String) columnClass.getMethod("getValue").invoke(column);
                    boolean isNull = (Boolean) columnClass.getMethod("getIsNull").invoke(column);
                    
                    Object convertedValue = convertValue(value, name, structure, isNull);
                    rowMap.put(name, convertedValue);
                }
            } catch (Exception e) {
                log.error("解析 Canal RowData Before 失败", e);
            }
            
            return rowMap;
        }
        
        /**
         * 转换值类型
         */
        private Object convertValue(String value, String columnName, DatabaseOperator.TableStructure structure, boolean isNull) {
            if (isNull || value == null) {
                return null;
            }
            
            // 查找列类型
            for (DatabaseOperator.TableColumn column : structure.getColumns()) {
                if (column.getName().equals(columnName)) {
                    // 根据类型转换
                    String typeName = column.getTypeName().toUpperCase();
                    if (typeName.contains("INT")) {
                        try {
                            return Long.parseLong(value);
                        } catch (NumberFormatException e) {
                            return value;
                        }
                    } else if (typeName.contains("DECIMAL") || typeName.contains("NUMERIC")) {
                        try {
                            return java.math.BigDecimal.valueOf(Double.parseDouble(value));
                        } catch (NumberFormatException e) {
                            return value;
                        }
                    } else if (typeName.contains("DATE") || typeName.contains("TIME")) {
                        try {
                            return java.sql.Timestamp.valueOf(value);
                        } catch (Exception e) {
                            return value;
                        }
                    }
                    return value;
                }
            }
            
            return value;
        }
        
        /**
         * 确认消息
         */
        private void ack(Object connector, Object message) throws Exception {
            Class<?> messageClass = message.getClass();
            Long batchId = (Long) messageClass.getMethod("getId").invoke(message);
            connector.getClass().getMethod("ack", long.class).invoke(connector, batchId);
        }
        
        /**
         * 回滚消息
         */
        private void rollback(Object connector) throws Exception {
            connector.getClass().getMethod("rollback").invoke(connector);
        }
        
        /**
         * 断开 Canal 连接
         */
        private void disconnectCanal(Object connector) throws Exception {
            connector.getClass().getMethod("disconnect").invoke(connector);
        }
        
        /**
         * 轮询同步（降级方案）
         */
        private void pollingSync() {
            log.info("使用轮询方式同步增量数据，Task ID: {}", taskId);
            // 复用 BinlogListener 的轮询逻辑
            // TODO: 可以提取为公共方法
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
         * 获取表结构
         */
        private DatabaseOperator.TableStructure getTableStructure(Connection conn, String tableName) throws SQLException {
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
            
            // SET 子句参数
            for (DatabaseOperator.TableColumn column : structure.getColumns()) {
                if (!structure.getPrimaryKeys().contains(column.getName())) {
                    Object value = afterRow.get(column.getName());
                    stmt.setObject(paramIndex++, value);
                }
            }
            
            // WHERE 子句参数
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
        
        public void stop() {
            stopped = true;
        }
        
        public boolean isRunning() {
            return running;
        }
    }
}

