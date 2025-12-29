package com.lixiangyu.common.migration.operator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.List;

/**
 * MyBatis 实现
 * 使用 MyBatis SqlSession 操作数据库
 * 
 * 注意：需要项目中有 MyBatis 配置
 * 
 * @author lixiangyu
 */
@Slf4j
@Component
public class MyBatisDatabaseOperator implements DatabaseOperator {
    
    private final Object sqlSessionTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    
    public MyBatisDatabaseOperator(Object sqlSessionFactory, 
                                   Object sqlSessionTemplate,
                                   DataSource dataSource) {
        // sqlSessionFactory 保留参数以保持接口一致性，但当前实现主要使用 sqlSessionTemplate
        this.sqlSessionTemplate = sqlSessionTemplate;
        this.dataSource = dataSource;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }
    
    @Override
    public Connection getConnection() throws Exception {
        // 使用反射调用 getConnection() 方法
        try {
            return (Connection) sqlSessionTemplate.getClass()
                    .getMethod("getConnection")
                    .invoke(sqlSessionTemplate);
        } catch (Exception e) {
            // 降级使用 DataSource
            return dataSource.getConnection();
        }
    }
    
    @Override
    public ResultSet executeQuery(String sql, Object... params) throws Exception {
        // MyBatis 通常使用 Mapper，这里降级使用 JdbcTemplate
        // 或者可以通过 SqlSession.selectList() 执行 SQL
        Connection conn = getConnection();
        java.sql.PreparedStatement stmt = conn.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            stmt.setObject(i + 1, params[i]);
        }
        return stmt.executeQuery();
    }
    
    @Override
    public int executeUpdate(String sql, Object... params) throws Exception {
        // 使用 JdbcTemplate 执行更新
        return jdbcTemplate.update(sql, params);
    }
    
    @Override
    public int[] executeBatch(String sql, List<Object[]> batchParams) throws Exception {
        // 使用 JdbcTemplate 批量更新
        return jdbcTemplate.batchUpdate(sql, batchParams);
    }
    
    @Override
    public long getRecordCount(String tableName) throws Exception {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName, Long.class);
        return count != null ? count : 0L;
    }
    
    @Override
    public TableStructure getTableStructure(String tableName) throws Exception {
        // 使用 JDBC 元数据获取表结构
        try (Connection conn = getConnection()) {
            return getTableStructureFromMetaData(conn, tableName);
        }
    }
    
    @Override
    public void createTable(String tableName, TableStructure structure) throws Exception {
        // 使用 JdbcTemplate 执行 DDL
        String ddl = buildCreateTableDDL(tableName, structure);
        jdbcTemplate.execute(ddl);
        log.info("创建表成功: {}", tableName);
    }
    
    @Override
    public void truncateTable(String tableName) throws Exception {
        jdbcTemplate.execute("TRUNCATE TABLE " + tableName);
        log.info("清空表成功: {}", tableName);
    }
    
    @Override
    public List<String> getPrimaryKeys(String tableName) throws Exception {
        try (Connection conn = getConnection()) {
            return getPrimaryKeysFromMetaData(conn, tableName);
        }
    }
    
    /**
     * 从元数据获取表结构
     */
    private TableStructure getTableStructureFromMetaData(Connection conn, String tableName) 
            throws Exception {
        TableStructure structure = new TableStructure();
        structure.setTableName(tableName);
        structure.setColumns(new java.util.ArrayList<>());
        
        java.sql.DatabaseMetaData metaData = conn.getMetaData();
        String catalog = conn.getCatalog();
        String schema = conn.getSchema();
        
        try (java.sql.ResultSet rs = metaData.getColumns(catalog, schema, tableName, null)) {
            while (rs.next()) {
                TableColumn column = new TableColumn();
                column.setName(rs.getString("COLUMN_NAME"));
                column.setType(rs.getInt("DATA_TYPE"));
                column.setTypeName(rs.getString("TYPE_NAME"));
                column.setSize(rs.getInt("COLUMN_SIZE"));
                column.setNullable(rs.getInt("NULLABLE") == java.sql.DatabaseMetaData.columnNullable);
                column.setDefaultValue(rs.getString("COLUMN_DEF"));
                structure.getColumns().add(column);
            }
        }
        
        structure.setPrimaryKeys(getPrimaryKeysFromMetaData(conn, tableName));
        
        return structure.getColumns().isEmpty() ? null : structure;
    }
    
    /**
     * 从元数据获取主键
     */
    private List<String> getPrimaryKeysFromMetaData(Connection conn, String tableName) 
            throws Exception {
        List<String> primaryKeys = new java.util.ArrayList<>();
        java.sql.DatabaseMetaData metaData = conn.getMetaData();
        String catalog = conn.getCatalog();
        String schema = conn.getSchema();
        
        try (java.sql.ResultSet rs = metaData.getPrimaryKeys(catalog, schema, tableName)) {
            while (rs.next()) {
                primaryKeys.add(rs.getString("COLUMN_NAME"));
            }
        }
        
        return primaryKeys;
    }
    
    /**
     * 构建 CREATE TABLE DDL
     */
    private String buildCreateTableDDL(String tableName, TableStructure structure) {
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
        return ddl.toString();
    }
}

