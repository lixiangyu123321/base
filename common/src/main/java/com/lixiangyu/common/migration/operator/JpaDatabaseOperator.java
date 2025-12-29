package com.lixiangyu.common.migration.operator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA 实现
 * 使用 JPA EntityManager 操作数据库
 * 
 * 功能特性：
 * 1. 支持 JPA 实体类操作
 * 2. 支持原生 SQL 查询
 * 3. 自动事务管理
 * 
 * @author lixiangyu
 */
@Slf4j
@Component
public class JpaDatabaseOperator implements DatabaseOperator {
    
    private final Object entityManager;  // 使用 Object 避免直接依赖
    @SuppressWarnings("unused")
    private final Object entityManagerFactory;  // 保留用于未来扩展
    @SuppressWarnings("unused")
    private final DataSource dataSource;  // 保留用于未来扩展
    
    public JpaDatabaseOperator(Object entityManager, 
                               Object entityManagerFactory,
                               DataSource dataSource) {
        this.entityManager = entityManager;
        this.entityManagerFactory = entityManagerFactory;
        this.dataSource = dataSource;
    }
    
    @Override
    public Connection getConnection() throws Exception {
        // JPA EntityManager 可以获取底层连接（使用反射）
        return (Connection) entityManager.getClass().getMethod("unwrap", Class.class)
                .invoke(entityManager, Connection.class);
    }
    
    @Override
    public ResultSet executeQuery(String sql, Object... params) throws Exception {
        // JPA 不直接返回 ResultSet，需要通过 Connection 执行
        Connection conn = getConnection();
        java.sql.PreparedStatement stmt = conn.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            stmt.setObject(i + 1, params[i]);
        }
        return stmt.executeQuery();
    }
    
    @Override
    public int executeUpdate(String sql, Object... params) throws Exception {
        // 使用 JPA 原生 SQL（使用反射）
        Object query = entityManager.getClass().getMethod("createNativeQuery", String.class)
                .invoke(entityManager, sql);
        for (int i = 0; i < params.length; i++) {
            query.getClass().getMethod("setParameter", int.class, Object.class)
                    .invoke(query, i + 1, params[i]);
        }
        return (Integer) query.getClass().getMethod("executeUpdate").invoke(query);
    }
    
    @Override
    public int[] executeBatch(String sql, List<Object[]> batchParams) throws Exception {
        // JPA 批量更新
        int[] results = new int[batchParams.size()];
        for (int i = 0; i < batchParams.size(); i++) {
            results[i] = executeUpdate(sql, batchParams.get(i));
        }
        return results;
    }
    
    @Override
    public long getRecordCount(String tableName) throws Exception {
        Object query = entityManager.getClass().getMethod("createNativeQuery", String.class)
                .invoke(entityManager, "SELECT COUNT(*) FROM " + tableName);
        Object result = query.getClass().getMethod("getSingleResult").invoke(query);
        return result != null ? ((Number) result).longValue() : 0L;
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
        // 使用原生 SQL 执行 DDL（使用反射）
        String ddl = buildCreateTableDDL(tableName, structure);
        Object query = entityManager.getClass().getMethod("createNativeQuery", String.class)
                .invoke(entityManager, ddl);
        query.getClass().getMethod("executeUpdate").invoke(query);
        log.info("创建表成功: {}", tableName);
    }
    
    @Override
    public void truncateTable(String tableName) throws Exception {
        Object query = entityManager.getClass().getMethod("createNativeQuery", String.class)
                .invoke(entityManager, "TRUNCATE TABLE " + tableName);
        query.getClass().getMethod("executeUpdate").invoke(query);
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
        structure.setColumns(new ArrayList<>());
        
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
        List<String> primaryKeys = new ArrayList<>();
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
    
    /**
     * 使用 JPA 保存实体（使用反射）
     */
    public <T> T save(T entity) throws Exception {
        entityManager.getClass().getMethod("persist", Object.class).invoke(entityManager, entity);
        return entity;
    }
    
    /**
     * 使用 JPA 更新实体（使用反射）
     */
    @SuppressWarnings("unchecked")
    public <T> T update(T entity) throws Exception {
        return (T) entityManager.getClass().getMethod("merge", Object.class).invoke(entityManager, entity);
    }
    
    /**
     * 使用 JPA 删除实体（使用反射）
     */
    public void delete(Object entity) throws Exception {
        entityManager.getClass().getMethod("remove", Object.class).invoke(entityManager, entity);
    }
    
    /**
     * 使用 JPA 根据 ID 查找实体（使用反射）
     */
    @SuppressWarnings("unchecked")
    public <T> T findById(Class<T> entityClass, Object id) throws Exception {
        return (T) entityManager.getClass().getMethod("find", Class.class, Object.class)
                .invoke(entityManager, entityClass, id);
    }
}

