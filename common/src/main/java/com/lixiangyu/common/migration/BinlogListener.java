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
 * TODO 对于增删改查的语句比较粗糙
 *
 * 12-31 18:01 依旧基于jdbc原生接口进行同步操作
 *              这里其实可以使用其他方法（MyBatis，Hibernate/JPA，JDBC-TEMPLATE，JDBC原生接口）进行数据库的迁移的
 * TODO 上面这里至关重要
 * @author lixiangyu
 */
@Slf4j
@Component
public class BinlogListener {
    
    /**
     * 监听任务映射（Task ID -> 监听任务）
     * 支持监听多个数据同步任务
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
                // ========== 反射调用：检查 BinaryLogClient 类是否存在 ==========
                // 类名：com.github.shyiko.mysql.binlog.BinaryLogClient
                // 作用：MySQL binlog 客户端，用于连接 MySQL 服务器并监听 binlog 事件
                // 方法：Class.forName(String className)
                // 说明：动态加载类，如果类不存在会抛出 ClassNotFoundException
                // 位置：BinlogListener.java:169
                Class.forName("com.github.shyiko.mysql.binlog.BinaryLogClient");
                Object client = createBinaryLogClient();
                
                // 设置事件监听器
                setEventListener(client);
                
                // 连接到 MySQL
                connect(client);
                
                // 保持运行
                // 一直保持连接，一直进行监听
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
                
                // ========== 反射调用：创建 BinaryLogClient 实例 ==========
                // 类名：com.github.shyiko.mysql.binlog.BinaryLogClient
                // 构造函数原型：BinaryLogClient(String hostname, int port)
                // 作用：创建 binlog 客户端实例，连接到指定的 MySQL 服务器
                // 参数：host - MySQL 服务器主机名，port - MySQL 服务器端口（通常是 3306）
                // 位置：BinlogListener.java:206
                Class<?> binaryLogClientClass = Class.forName("com.github.shyiko.mysql.binlog.BinaryLogClient");
                Object client = binaryLogClientClass.getConstructor(String.class, int.class).newInstance(host, port);
                
                // ========== 反射调用：设置 BinaryLogClient 用户名 ==========
                // 方法原型：void setUsername(String username)
                // 作用：设置连接 MySQL 服务器的用户名
                // 参数：username - MySQL 用户名
                // 位置：BinlogListener.java:208
                binaryLogClientClass.getMethod("setUsername", String.class).invoke(client, username);

                // ========== 反射调用：设置 binlog 文件名（用于断点续传） ==========
                // 方法原型：void setBinlogFilename(String binlogFilename)
                // 作用：设置要读取的 binlog 文件名，用于断点续传功能
                // 参数：binlogFilename - binlog 文件名（如 "mysql-bin.000001"）
                // 位置：BinlogListener.java:212
                if (config.getBinlogFile() != null) {
                    binaryLogClientClass.getMethod("setBinlogFilename", String.class).invoke(client, config.getBinlogFile());
                }
                
                // ========== 反射调用：设置 binlog 位置（用于断点续传） ==========
                // 方法原型：void setBinlogPosition(long binlogPosition)
                // 作用：设置要读取的 binlog 位置（字节偏移），用于断点续传功能
                // 参数：binlogPosition - binlog 位置（字节偏移量）
                // 位置：BinlogListener.java:215
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
            
            // ========== 反射调用：加载 EventListener 接口 ==========
            // 类名：com.github.shyiko.mysql.binlog.event.EventListener
            // 作用：binlog 事件监听器接口，用于接收 binlog 事件
            // 方法：void onEvent(Event event) - 当收到 binlog 事件时调用
            // 位置：BinlogListener.java:227
            Class<?> eventListenerClass = Class.forName("com.github.shyiko.mysql.binlog.event.EventListener");

            // ========== 反射调用：创建动态代理实现 EventListener 接口 ==========
            // 方法：Proxy.newProxyInstance(ClassLoader loader, Class<?>[] interfaces, InvocationHandler h)
            // 作用：动态创建 EventListener 接口的实现类，将事件转发给 handleBinlogEvent 方法
            // 接口方法：void onEvent(Event event) - 当收到 binlog 事件时调用
            // 说明：使用动态代理避免直接实现接口，实现编译期无依赖
            // 位置：BinlogListener.java:230-239
            Object listener = java.lang.reflect.Proxy.newProxyInstance(
                    eventListenerClass.getClassLoader(),
                    new Class[]{eventListenerClass},
                    (proxy, method, args) -> {
                        if ("onEvent".equals(method.getName())) {
                            // 第一个参数为 Event 对象，为事件对象
                            handleBinlogEvent(args[0]);
                        }
                        return null;
                    });
            
            // ========== 反射调用：注册事件监听器到 BinaryLogClient ==========
            // 方法原型：void registerEventListener(EventListener listener)
            // 作用：将事件监听器注册到 BinaryLogClient，当收到 binlog 事件时会调用监听器的 onEvent 方法
            // 参数：listener - 事件监听器实例
            // 位置：BinlogListener.java:241
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
            // ========== 反射调用：获取 binlog 事件中的表ID ==========
            // 方法原型：long getTableId()
            // 作用：获取 binlog 事件对应的表ID（MySQL 内部表标识符）
            // 返回：表ID（long 类型）
            // 位置：BinlogListener.java:272
            long tableId = (Long) event.getClass().getMethod("getTableId").invoke(event);
            
            // ========== 反射调用：获取 binlog 事件中的行数据 ==========
            // 方法原型：List<Row> getRows()
            // 作用：获取 binlog 事件中包含的所有行数据（INSERT 事件包含插入的行）
            // 返回：行数据列表（List<Row> 类型）
            // 位置：BinlogListener.java:273
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
            // ========== 反射调用：获取 binlog UPDATE 事件中的表ID ==========
            // 方法原型：long getTableId()
            // 作用：获取 UPDATE 事件对应的表ID
            // 位置：BinlogListener.java:289
            long tableId = (Long) event.getClass().getMethod("getTableId").invoke(event);
            
            // ========== 反射调用：获取 binlog UPDATE 事件中的行数据 ==========
            // 方法原型：List<Pair<Row, Row>> getRows()
            // 作用：获取 UPDATE 事件中包含的行数据对（Pair<beforeRow, afterRow>）
            // 说明：在 binlog_row_image = FULL 模式下，包含完整的 before 和 after rows
            // 位置：BinlogListener.java:290
            Object rows = event.getClass().getMethod("getRows").invoke(event);
            
            String tableName = getTableName(tableId);
            if (tableName == null || !shouldListenTable(tableName)) {
                return;
            }
            
            // 检查 binlog_row_image 配置
            String binlogRowImage = getBinlogRowImage();
            if (!"FULL".equalsIgnoreCase(binlogRowImage)) {
                log.warn("binlog_row_image 配置为 {}，UPDATE 事件的 before/after rows 解析可能不完整。建议设置为 FULL", binlogRowImage);
            }
            
            syncUpdateToTarget(tableName, rows, binlogRowImage);
        }
        
        /**
         * 处理 DELETE 事件
         */
        private void handleDeleteEvent(Object event) throws Exception {
            // ========== 反射调用：获取 binlog DELETE 事件中的表ID ==========
            // 方法原型：long getTableId()
            // 作用：获取 DELETE 事件对应的表ID
            // 位置：BinlogListener.java:310
            long tableId = (Long) event.getClass().getMethod("getTableId").invoke(event);
            
            // ========== 反射调用：获取 binlog DELETE 事件中的行数据 ==========
            // 方法原型：List<Row> getRows()
            // 作用：获取 DELETE 事件中包含的所有行数据（被删除的行）
            // 位置：BinlogListener.java:311
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
         * 
         * @param tableName 表名
         * @param rows UPDATE 事件的 rows（List<Pair<Row, Row>> 或 List<Row>）
         * @param binlogRowImage binlog_row_image 配置值（FULL/MINIMAL/NOBLOB）
         */
        private void syncUpdateToTarget(String tableName, Object rows, String binlogRowImage) throws SQLException {
            log.debug("同步 UPDATE 到目标表: {}, binlog_row_image: {}", tableName, binlogRowImage);
            
            try (java.sql.Connection conn = config.getTargetDataSource().getConnection()) {
                // 获取表结构
                DatabaseOperator.TableStructure structure = getTableStructure(conn, tableName);
                if (structure == null) {
                    log.warn("表 {} 不存在，跳过 UPDATE 同步", tableName);
                    return;
                }
                
                // 只有在 FULL 模式下才能正确解析 before 和 after rows
                if (!"FULL".equalsIgnoreCase(binlogRowImage)) {
                    log.warn("binlog_row_image 为 {}，UPDATE 事件可能不包含完整的 before/after rows，同步可能不准确", binlogRowImage);
                }
                
                // 解析 rows（binlog UPDATE 事件在 FULL 模式下包含 before 和 after 两行）
                List<Map<String, Object>> beforeRows = parseRowsBefore(rows, structure, binlogRowImage);
                List<Map<String, Object>> afterRows = parseRowsAfter(rows, structure, binlogRowImage);
                
                if (afterRows.isEmpty()) {
                    log.warn("UPDATE 事件未解析到 after rows，跳过同步");
                    return;
                }
                
                // 构建 UPDATE SQL（基于主键更新非主键列）
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
            // List 表示每行记录 Map为表字段与具体值的映射
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
                // ========== 反射调用：获取 binlog Row 对象中的列值 ==========
                // 方法原型：Object getValue(int columnIndex)
                // 作用：从 binlog Row 对象中获取指定索引位置的列值
                // 参数：columnIndex - 列索引（从 0 开始）
                // 返回：列值（Object 类型，可能是 String、Long、Date 等）
                // 说明：binlog 事件中的 Row 对象包含行的所有列值
                // 位置：BinlogListener.java:489-492
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
         * 
         * 注意：只有在 binlog_row_image = FULL 时，UPDATE 事件才包含完整的 before 和 after rows
         * 
         * @param rows UPDATE 事件的 rows（List<Pair<Row, Row>> 类型）
         * @param structure 表结构
         * @param binlogRowImage binlog_row_image 配置值
         * @return before rows 列表
         */
        private List<Map<String, Object>> parseRowsBefore(Object rows, 
                                                          DatabaseOperator.TableStructure structure,
                                                          String binlogRowImage) {
            List<Map<String, Object>> beforeRows = new java.util.ArrayList<>();
            
            // 只有在 FULL 模式下才能正确解析 before rows
            if (!"FULL".equalsIgnoreCase(binlogRowImage)) {
                log.debug("binlog_row_image 为 {}，无法解析完整的 before rows", binlogRowImage);
                return beforeRows;
            }
            
            try {
                // UPDATE 事件的 rows 是 List<Pair<Row, Row>> 类型
                // Pair 的第一个元素是 before row，第二个元素是 after row
                if (rows instanceof java.util.List) {
                    java.util.List<?> rowList = (java.util.List<?>) rows;
                    
                    for (Object pair : rowList) {
                        // 尝试获取 Pair 的第一个元素（before row）
                        Object beforeRow = extractPairFirst(pair);
                        if (beforeRow != null) {
                            Map<String, Object> rowData = parseRow(beforeRow, structure);
                            if (rowData != null) {
                                beforeRows.add(rowData);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("解析 UPDATE 事件的 before rows 失败", e);
            }
            
            return beforeRows;
        }
        
        /**
         * 解析 UPDATE 事件的 after rows
         * 
         * 注意：只有在 binlog_row_image = FULL 时，UPDATE 事件才包含完整的 before 和 after rows
         * 
         * @param rows UPDATE 事件的 rows（List<Pair<Row, Row>> 类型）
         * @param structure 表结构
         * @param binlogRowImage binlog_row_image 配置值
         * @return after rows 列表
         */
        private List<Map<String, Object>> parseRowsAfter(Object rows, 
                                                         DatabaseOperator.TableStructure structure,
                                                         String binlogRowImage) {
            List<Map<String, Object>> afterRows = new java.util.ArrayList<>();
            
            // 只有在 FULL 模式下才能正确解析 after rows
            if (!"FULL".equalsIgnoreCase(binlogRowImage)) {
                log.debug("binlog_row_image 为 {}，无法解析完整的 after rows", binlogRowImage);
                return afterRows;
            }
            
            try {
                // UPDATE 事件的 rows 是 List<Pair<Row, Row>> 类型
                // Pair 的第一个元素是 before row，第二个元素是 after row
                if (rows instanceof java.util.List) {
                    java.util.List<?> rowList = (java.util.List<?>) rows;
                    
                    for (Object pair : rowList) {
                        // 尝试获取 Pair 的第二个元素（after row）
                        Object afterRow = extractPairSecond(pair);
                        if (afterRow != null) {
                            Map<String, Object> rowData = parseRow(afterRow, structure);
                            if (rowData != null) {
                                afterRows.add(rowData);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("解析 UPDATE 事件的 after rows 失败", e);
            }
            
            return afterRows;
        }
        
        /**
         * 从 Pair 对象中提取第一个元素（before row）
         * 
         * @param pair Pair 对象（可能是 com.github.shyiko.mysql.binlog.event.RowsEvent.Pair 或其他实现）
         * @return before row 对象
         */
        private Object extractPairFirst(Object pair) {
            try {
                // ========== 反射调用：获取 Pair 对象的所有方法 ==========
                // 方法：Class.getMethods()
                // 作用：获取 Pair 对象的所有公共方法，用于查找获取第一个元素的方法
                // 返回：Method[] - 方法数组
                // 位置：BinlogListener.java:606
                java.lang.reflect.Method[] methods = pair.getClass().getMethods();
                
                // ========== 反射调用：尝试通过 getKey() 方法获取 Pair 的第一个元素 ==========
                // 方法原型：Object getKey() 或 K getKey()
                // 作用：获取 Pair 对象的第一个元素（before row）
                // 说明：某些 Pair 实现（如 Map.Entry）使用 getKey() 方法
                // 位置：BinlogListener.java:609-613
                for (java.lang.reflect.Method method : methods) {
                    if ("getKey".equals(method.getName()) && method.getParameterCount() == 0) {
                        return method.invoke(pair);
                    }
                }
                
                // ========== 反射调用：尝试通过 getFirst() 方法获取 Pair 的第一个元素 ==========
                // 方法原型：Object getFirst() 或 K getFirst()
                // 作用：获取 Pair 对象的第一个元素（before row）
                // 说明：某些 Pair 实现（如 Apache Commons Pair）使用 getFirst() 方法
                // 位置：BinlogListener.java:616-620
                for (java.lang.reflect.Method method : methods) {
                    if ("getFirst".equals(method.getName()) && method.getParameterCount() == 0) {
                        return method.invoke(pair);
                    }
                }
                
                // ========== 反射调用：尝试通过 getLeft() 方法获取 Pair 的第一个元素 ==========
                // 方法原型：Object getLeft() 或 K getLeft()
                // 作用：获取 Pair 对象的第一个元素（before row）
                // 说明：某些 Pair 实现使用 getLeft() 方法
                // 位置：BinlogListener.java:623-627
                for (java.lang.reflect.Method method : methods) {
                    if ("getLeft".equals(method.getName()) && method.getParameterCount() == 0) {
                        return method.invoke(pair);
                    }
                }
                
                // ========== 反射调用：尝试通过字段访问获取 Pair 的第一个元素 ==========
                // 字段名：key 或 first
                // 作用：如果 Pair 使用字段存储第一个元素，直接访问字段
                // 方法：getDeclaredField(String name) - 获取字段，setAccessible(true) - 设置可访问
                // 位置：BinlogListener.java:631-644
                try {
                    java.lang.reflect.Field field = pair.getClass().getDeclaredField("key");
                    field.setAccessible(true);
                    return field.get(pair);
                } catch (NoSuchFieldException e) {
                    // 忽略
                }
                
                try {
                    java.lang.reflect.Field field = pair.getClass().getDeclaredField("first");
                    field.setAccessible(true);
                    return field.get(pair);
                } catch (NoSuchFieldException e) {
                    // 忽略
                }
                
                log.warn("无法从 Pair 对象中提取第一个元素，Pair 类型: {}", pair.getClass().getName());
            } catch (Exception e) {
                log.error("提取 Pair 第一个元素失败", e);
            }
            
            return null;
        }
        
        /**
         * 从 Pair 对象中提取第二个元素（after row）
         * 
         * @param pair Pair 对象（可能是 com.github.shyiko.mysql.binlog.event.RowsEvent.Pair 或其他实现）
         * @return after row 对象
         */
        private Object extractPairSecond(Object pair) {
            try {
                // ========== 反射调用：获取 Pair 对象的所有方法 ==========
                // 方法：Class.getMethods()
                // 作用：获取 Pair 对象的所有公共方法，用于查找获取第二个元素的方法
                // 位置：BinlogListener.java:664
                java.lang.reflect.Method[] methods = pair.getClass().getMethods();
                
                // ========== 反射调用：尝试通过 getValue() 方法获取 Pair 的第二个元素 ==========
                // 方法原型：Object getValue() 或 V getValue()
                // 作用：获取 Pair 对象的第二个元素（after row）
                // 说明：某些 Pair 实现（如 Map.Entry）使用 getValue() 方法
                // 位置：BinlogListener.java:667-671
                for (java.lang.reflect.Method method : methods) {
                    if ("getValue".equals(method.getName()) && method.getParameterCount() == 0) {
                        return method.invoke(pair);
                    }
                }
                
                // ========== 反射调用：尝试通过 getSecond() 方法获取 Pair 的第二个元素 ==========
                // 方法原型：Object getSecond() 或 V getSecond()
                // 作用：获取 Pair 对象的第二个元素（after row）
                // 说明：某些 Pair 实现（如 Apache Commons Pair）使用 getSecond() 方法
                // 位置：BinlogListener.java:674-678
                for (java.lang.reflect.Method method : methods) {
                    if ("getSecond".equals(method.getName()) && method.getParameterCount() == 0) {
                        return method.invoke(pair);
                    }
                }
                
                // ========== 反射调用：尝试通过 getRight() 方法获取 Pair 的第二个元素 ==========
                // 方法原型：Object getRight() 或 V getRight()
                // 作用：获取 Pair 对象的第二个元素（after row）
                // 说明：某些 Pair 实现使用 getRight() 方法
                // 位置：BinlogListener.java:681-685
                for (java.lang.reflect.Method method : methods) {
                    if ("getRight".equals(method.getName()) && method.getParameterCount() == 0) {
                        return method.invoke(pair);
                    }
                }
                
                // ========== 反射调用：尝试通过字段访问获取 Pair 的第二个元素 ==========
                // 字段名：value 或 second
                // 作用：如果 Pair 使用字段存储第二个元素，直接访问字段
                // 方法：getDeclaredField(String name) - 获取字段，setAccessible(true) - 设置可访问
                // 位置：BinlogListener.java:689-702
                try {
                    java.lang.reflect.Field field = pair.getClass().getDeclaredField("value");
                    field.setAccessible(true);
                    return field.get(pair);
                } catch (NoSuchFieldException e) {
                    // 忽略
                }
                
                try {
                    java.lang.reflect.Field field = pair.getClass().getDeclaredField("second");
                    field.setAccessible(true);
                    return field.get(pair);
                } catch (NoSuchFieldException e) {
                    // 忽略
                }
                
                log.warn("无法从 Pair 对象中提取第二个元素，Pair 类型: {}", pair.getClass().getName());
            } catch (Exception e) {
                log.error("提取 Pair 第二个元素失败", e);
            }
            
            return null;
        }
        
        /**
         * 获取 MySQL binlog_row_image 配置值
         * 
         * @return binlog_row_image 配置值（FULL/MINIMAL/NOBLOB），如果无法获取则返回 "UNKNOWN"
         */
        private String getBinlogRowImage() {
            try (java.sql.Connection conn = config.getSourceDataSource().getConnection()) {
                try (java.sql.Statement stmt = conn.createStatement();
                     java.sql.ResultSet rs = stmt.executeQuery("SHOW VARIABLES LIKE 'binlog_row_image'")) {
                    
                    if (rs.next()) {
                        String value = rs.getString("Value");
                        log.debug("binlog_row_image 配置值: {}", value);
                        return value != null ? value : "UNKNOWN";
                    }
                }
            } catch (Exception e) {
                log.warn("无法获取 binlog_row_image 配置，将使用默认处理方式", e);
            }
            
            return "UNKNOWN";
        }
        
        /**
         * 构建 INSERT SQL
         * 这里是预编译SQL
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
         * 基于主键进行更新
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
         * 基于主键进行删除操作
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
        /**
         * 连接 BinaryLogClient
         */
        private void connect(Object client) throws Exception {
            // ========== 反射调用：连接 BinaryLogClient 到 MySQL 服务器 ==========
            // 方法原型：void connect()
            // 作用：建立与 MySQL 服务器的连接，开始监听 binlog 事件
            // 说明：连接成功后，BinaryLogClient 会自动接收 binlog 事件并调用注册的监听器
            // 位置：BinlogListener.java:1057
            client.getClass().getMethod("connect").invoke(client);
        }
        
        /**
         * 断开 BinaryLogClient
         */
        private void disconnect(Object client) throws Exception {
            // ========== 反射调用：断开 BinaryLogClient 连接 ==========
            // 方法原型：void disconnect()
            // 作用：断开与 MySQL 服务器的连接，停止监听 binlog 事件
            // 位置：BinlogListener.java:1064
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

