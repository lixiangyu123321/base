package com.lixiangyu.common.migration.operator;

import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC 原生接口实现
 * 使用 JDBC 原生 API 操作数据库
 * 基于原生的Statement发送SQL语句，ResultSet返回结果
 * 注意：此类由 DatabaseOperatorFactory 创建，不应被 Spring 自动管理
 * 
 * @author lixiangyu
 */
@Slf4j
public class JdbcDatabaseOperator implements DatabaseOperator {
    
    private final DataSource dataSource;
    private Connection connection;

    /**
     * 注入数据源
     * @param dataSource spring注入数据源
     */
    public JdbcDatabaseOperator(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    @Override
    public Connection getConnection() throws Exception {
        if (connection == null || connection.isClosed()) {
            connection = dataSource.getConnection();
        }
        return connection;
    }

    /**
     * 预编译sql执行
     * @param sql SQL 语句
     * @param params 参数
     * @return 查询结果
     * @throws Exception 统一异常
     */
    @Override
    public ResultSet executeQuery(String sql, Object... params) throws Exception {
        Connection conn = getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql);
        setParameters(stmt, params);
        return stmt.executeQuery();
    }
    
    @Override
    public int executeUpdate(String sql, Object... params) throws Exception {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            setParameters(stmt, params);
            return stmt.executeUpdate();
        }
    }
    
    @Override
    public int[] executeBatch(String sql, List<Object[]> batchParams) throws Exception {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (Object[] params : batchParams) {
                setParameters(stmt, params);
                stmt.addBatch();
            }
            return stmt.executeBatch();
        }
    }
    
    @Override
    public long getRecordCount(String tableName) throws Exception {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(1) FROM " + tableName)) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        return 0;
    }

    /**
     * 基于Connect结构的 DatabaseMetaData 获得源数据
     * @param tableName 表名
     * @return 返回自定义表结构
     * @throws Exception 统一抛出异常
     */
    @Override
    public TableStructure getTableStructure(String tableName) throws Exception {
        TableStructure structure = new TableStructure();
        structure.setTableName(tableName);
        structure.setColumns(new ArrayList<>());
        
        try (Connection conn = getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            String catalog = conn.getCatalog();
            String schema = conn.getSchema();
            
            // 获取列信息
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
    
    @Override
    public void createTable(String tableName, TableStructure structure) throws Exception {
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
        
        if (!structure.getPrimaryKeys().isEmpty()) {
            ddl.append(", PRIMARY KEY (");
            ddl.append(String.join(", ", structure.getPrimaryKeys()));
            ddl.append(")");
        }
        
        ddl.append(")");
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(ddl.toString());
            log.info("创建表成功: {}", tableName);
        }
    }
    
    @Override
    public void truncateTable(String tableName) throws Exception {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE TABLE " + tableName);
            log.info("清空表成功: {}", tableName);
        }
    }
    
    @Override
    public List<String> getPrimaryKeys(String tableName) throws Exception {
        List<String> primaryKeys = new ArrayList<>();
        try (Connection conn = getConnection()) {
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
     * 设置 PreparedStatement 参数
     */
    private void setParameters(PreparedStatement stmt, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            stmt.setObject(i + 1, params[i]);
        }
    }
    
    /**
     * 关闭连接
     */
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}

