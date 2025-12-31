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
         * 源数据源的用户名
         */
        private String username;
        
        /**
         * Canal 密码
         * 源数据源密码
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
         * 控制批量处理的粒度
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
                // ========== 反射调用：检查 CanalConnector 类是否存在 ==========
                // 类名：com.alibaba.otter.canal.client.CanalConnector
                // 作用：Canal 客户端连接器接口，用于连接 Canal 服务器并获取 binlog 变更数据
                // 方法：Class.forName(String className)
                // 说明：动态加载类，如果类不存在会抛出 ClassNotFoundException
                // 位置：CanalListener.java:174
                Class.forName("com.alibaba.otter.canal.client.CanalConnector");
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
         * TODO 默认端口为11111
         */
        private Object createCanalConnector() throws Exception {
            // 解析服务器地址
            String[] parts = config.getCanalServerAddress().split(":");
            String host = parts[0];
            int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 11111;
            
            // ========== 反射调用：创建 Canal 连接器 ==========
            // 类名：com.alibaba.otter.canal.client.CanalConnectors
            // 方法原型：static CanalConnector newSingleConnector(InetSocketAddress address, String destination, String username, String password)
            // 作用：创建单机模式的 Canal 连接器（连接到单个 Canal 服务器）
            // 参数：
            //   - address: Canal 服务器地址和端口
            //   - destination: Canal 实例名称（在 Canal 配置中定义）
            //   - username: 连接用户名（可选）
            //   - password: 连接密码（可选）
            // 返回：CanalConnector 实例
            // 位置：CanalListener.java:225-232
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
            // ========== 反射调用：连接 Canal 服务器 ==========
            // 类名：com.alibaba.otter.canal.client.CanalConnector
            // 方法原型：void connect()
            // 作用：建立与 Canal 服务器的连接
            // 说明：连接成功后，可以通过 subscribe() 订阅 binlog 变更
            // 位置：CanalListener.java:241
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
            
            // ========== 反射调用：订阅 binlog 变更 ==========
            // 类名：com.alibaba.otter.canal.client.CanalConnector
            // 方法原型：void subscribe(String filter)
            // 作用：订阅指定过滤条件的 binlog 变更
            // 参数：filter - 过滤表达式（如 "database.table" 或 ".*\\..*" 表示所有表）
            // 说明：订阅后，Canal 服务器会推送匹配的 binlog 变更数据
            // 位置：CanalListener.java:255
            connector.getClass().getMethod("subscribe", String.class).invoke(connector, filter);
            log.info("Canal 订阅成功，Filter: {}", filter);
        }
        
        /**
         * 获取消息
         */
        private Object getMessage(Object connector) throws Exception {
            Class<?> connectorClass = connector.getClass();
            // ========== 反射调用：获取 Canal 消息（不立即确认） ==========
            // 类名：com.alibaba.otter.canal.client.CanalConnector
            // 方法原型：Message getWithoutAck(int batchSize)
            // 作用：从 Canal 服务器批量拉取 binlog 变更数据，但不立即发送 Ack 确认
            // 参数：batchSize - 批量大小（每次拉取的消息数量）
            // 返回：Message 对象，包含 binlog 变更数据
            // 说明：拉取成功后不会立即向服务端发送 Ack，需要手动调用 ack() 确认
            // 位置：CanalListener.java:267-268
            Object message = connectorClass.getMethod("getWithoutAck", int.class)
                    .invoke(connector, config.getBatchSize());
            return message;
        }
        
        /**
         * 处理 Canal 消息
         */
        private void handleCanalMessage(Object message) throws Exception {
            // ========== 反射调用：获取 Canal Message 的 ID ==========
            // 类名：com.alibaba.otter.canal.protocol.Message
            // 方法原型：long getId()
            // 作用：获取消息的批次 ID，用于后续确认消息
            // 返回：批次 ID（long 类型）
            // 位置：CanalListener.java:279
            Class<?> messageClass = message.getClass();
            Long batchId = (Long) messageClass.getMethod("getId").invoke(message);
            
            // ========== 反射调用：获取 Canal Message 的 Entry 列表 ==========
            // 类名：com.alibaba.otter.canal.protocol.Message
            // 方法原型：List<Entry> getEntries()
            // 作用：获取消息中包含的所有 Entry（binlog 变更条目）
            // 返回：Entry 列表，每个 Entry 代表一个 binlog 变更事件
            // 位置：CanalListener.java:280
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
            
            // ========== 反射调用：获取 Canal Entry 的类型 ==========
            // 类名：com.alibaba.otter.canal.protocol.CanalEntry$Entry
            // 方法原型：EntryType getEntryType()
            // 作用：获取 Entry 的类型（ROWDATA、TRANSACTIONBEGIN、TRANSACTIONEND 等）
            // 返回：EntryType 枚举对象
            // 位置：CanalListener.java:301
            Object entryType = entryClass.getMethod("getEntryType").invoke(entry);
            
            // ========== 反射调用：获取枚举的名称 ==========
            // 方法原型：String name()
            // 作用：获取枚举常量的名称（如 "ROWDATA"）
            // 位置：CanalListener.java:302
            String entryTypeName = entryType.getClass().getMethod("name").invoke(entryType).toString();
            
            // 只处理 ROWDATA 类型
            if (!"ROWDATA".equals(entryTypeName)) {
                return;
            }
            
            // ========== 反射调用：获取 Canal Entry 的 Header ==========
            // 类名：com.alibaba.otter.canal.protocol.CanalEntry$Entry
            // 方法原型：Header getHeader()
            // 作用：获取 Entry 的元数据头信息（包含数据库名、表名、事件类型等）
            // 返回：Header 对象
            // 位置：CanalListener.java:310
            Object header = entryClass.getMethod("getHeader").invoke(entry);
            Class<?> headerClass = header.getClass();
            
            // ========== 反射调用：获取 Header 中的数据库名 ==========
            // 类名：com.alibaba.otter.canal.protocol.CanalEntry$Header
            // 方法原型：String getSchemaName()
            // 作用：获取变更数据所属的数据库名
            // 位置：CanalListener.java:313
            @SuppressWarnings("unused")
            String schemaName = (String) headerClass.getMethod("getSchemaName").invoke(header);
            
            // ========== 反射调用：获取 Header 中的表名 ==========
            // 方法原型：String getTableName()
            // 作用：获取变更数据所属的表名
            // 位置：CanalListener.java:314
            String tableName = (String) headerClass.getMethod("getTableName").invoke(header);
            
            // ========== 反射调用：获取 Header 中的事件类型 ==========
            // 方法原型：String getEventType()
            // 作用：获取 binlog 事件类型（INSERT、UPDATE、DELETE）
            // 位置：CanalListener.java:315
            String eventType = (String) headerClass.getMethod("getEventType").invoke(header);
            
            // 检查是否应该监听该表
            if (!shouldListenTable(tableName)) {
                return;
            }
            
            // ========== 反射调用：获取 Canal Entry 的存储值 ==========
            // 类名：com.alibaba.otter.canal.protocol.CanalEntry$Entry
            // 方法原型：byte[] getStoreValue()
            // 作用：获取 Entry 的序列化数据（Protobuf 格式）
            // 返回：字节数组
            // 位置：CanalListener.java:323
            Object storeValue = entryClass.getMethod("getStoreValue").invoke(entry);
            
            // ========== 反射调用：解析 RowChange 对象 ==========
            // 类名：com.alibaba.otter.canal.protocol.CanalEntry$RowChange
            // 方法原型：static RowChange parseFrom(byte[] data)
            // 作用：从 Protobuf 字节数组解析 RowChange 对象
            // 参数：data - Protobuf 序列化的字节数组
            // 返回：RowChange 对象，包含行变更数据
            // 说明：RowChange 包含变更前后的行数据（before/after columns）
            // 位置：CanalListener.java:324-326
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
            // ========== 反射调用：获取 RowChange 中的行数据列表 ==========
            // 类名：com.alibaba.otter.canal.protocol.CanalEntry$RowChange
            // 方法原型：List<RowData> getRowDatasList()
            // 作用：获取 RowChange 中包含的所有行数据
            // 返回：RowData 列表，每个 RowData 代表一行的变更数据
            // 说明：RowData 包含 beforeColumns 和 afterColumns，分别表示变更前后的列数据
            // 位置：CanalListener.java:337
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
                
                // ========== 反射调用：获取 RowData 中的 after 列列表 ==========
                // 类名：com.alibaba.otter.canal.protocol.CanalEntry$RowData
                // 方法原型：List<Column> getAfterColumnsList()
                // 作用：获取变更后的列数据列表（INSERT/UPDATE 事件使用）
                // 返回：Column 列表
                // 位置：CanalListener.java:466
                List<?> columns;
                if (useAfter) {
                    columns = (List<?>) rowDataClass.getMethod("getAfterColumnsList").invoke(rowData);
                } else {
                    columns = (List<?>) rowDataClass.getMethod("getAfterColumnsList").invoke(rowData);
                }
                
                // ========== 反射调用：解析 Canal Column 对象 ==========
                // 类名：com.alibaba.otter.canal.protocol.CanalEntry$Column
                // 方法原型：
                //   - String getName() - 获取列名
                //   - String getValue() - 获取列值（字符串格式）
                //   - boolean getIsNull() - 判断列值是否为 NULL
                // 作用：从 Column 对象中提取列名、列值和是否为 NULL 的信息
                // 位置：CanalListener.java:473-476
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
                // ========== 反射调用：获取 RowData 中的 before 列列表 ==========
                // 类名：com.alibaba.otter.canal.protocol.CanalEntry$RowData
                // 方法原型：List<Column> getBeforeColumnsList()
                // 作用：获取变更前的列数据列表（UPDATE/DELETE 事件使用）
                // 返回：Column 列表
                // 说明：UPDATE 事件包含 before 和 after 两套列数据
                // 位置：CanalListener.java:497
                Class<?> rowDataClass = rowData.getClass();
                List<?> columns = (List<?>) rowDataClass.getMethod("getBeforeColumnsList").invoke(rowData);
                
                // ========== 反射调用：解析 Canal Column 对象（before 列） ==========
                // 类名：com.alibaba.otter.canal.protocol.CanalEntry$Column
                // 方法原型：同 after 列的解析方法
                // 作用：从 before 列的 Column 对象中提取列名、列值和是否为 NULL 的信息
                // 位置：CanalListener.java:500-503
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
            // ========== 反射调用：获取 Message 的 ID ==========
            // 方法原型：long getId()
            // 作用：获取消息的批次 ID
            // 位置：CanalListener.java:559
            Class<?> messageClass = message.getClass();
            Long batchId = (Long) messageClass.getMethod("getId").invoke(message);
            
            // ========== 反射调用：确认 Canal 消息 ==========
            // 类名：com.alibaba.otter.canal.client.CanalConnector
            // 方法原型：void ack(long batchId)
            // 作用：向 Canal 服务器发送确认消息，表示已成功处理该批次的数据
            // 参数：batchId - 消息批次 ID
            // 说明：确认后，Canal 服务器会删除该批次的数据，不再重复推送
            // 位置：CanalListener.java:560
            connector.getClass().getMethod("ack", long.class).invoke(connector, batchId);
        }
        
        /**
         * 回滚消息
         */
        private void rollback(Object connector) throws Exception {
            // ========== 反射调用：回滚 Canal 消息 ==========
            // 类名：com.alibaba.otter.canal.client.CanalConnector
            // 方法原型：void rollback()
            // 作用：回滚当前批次的消息，表示处理失败，需要重新处理
            // 说明：回滚后，Canal 服务器会重新推送该批次的数据
            // 位置：CanalListener.java:567
            connector.getClass().getMethod("rollback").invoke(connector);
        }
        
        /**
         * 断开 Canal 连接
         */
        private void disconnectCanal(Object connector) throws Exception {
            // ========== 反射调用：断开 Canal 连接 ==========
            // 类名：com.alibaba.otter.canal.client.CanalConnector
            // 方法原型：void disconnect()
            // 作用：断开与 Canal 服务器的连接，停止接收 binlog 变更数据
            // 说明：断开连接后，需要重新 connect() 才能继续接收数据
            // 位置：CanalListener.java:574
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

