package com.lixiangyu.common.migration.operator;

import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
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
public class JpaDatabaseOperator implements DatabaseOperator {

    /**
     * EntityManager 包装类
     * 使用包装类避免直接依赖 JPA 类型，同时保持类型安全
     */
    private final EntityManagerWrapper entityManagerWrapper;
    
    /**
     * EntityManagerFactory 包装类
     * 保留用于未来扩展
     */
    @SuppressWarnings("unused")
    private final EntityManagerFactoryWrapper entityManagerFactoryWrapper;
    
    /**
     * 数据源（备用，用于直接获取连接）
     */
    @SuppressWarnings("unused")
    private final DataSource dataSource;

    /**
     * 构造函数
     * 
     * @param entityManager EntityManager 实例（通过反射获取）
     * @param entityManagerFactory EntityManagerFactory 实例（通过反射获取）
     * @param dataSource 数据源（备用）
     */
    public JpaDatabaseOperator(Object entityManager,
                               Object entityManagerFactory,
                               DataSource dataSource) {
        if (entityManager == null) {
            throw new IllegalArgumentException("EntityManager 不能为 null");
        }
        if (entityManagerFactory == null) {
            throw new IllegalArgumentException("EntityManagerFactory 不能为 null");
        }
        this.entityManagerWrapper = new EntityManagerWrapper(entityManager);
        this.entityManagerFactoryWrapper = new EntityManagerFactoryWrapper(entityManagerFactory);
        this.dataSource = dataSource;
    }
    
    /**
     * EntityManager 包装类
     * 封装反射调用，提供类型安全的接口
     */
    private static class EntityManagerWrapper {
        private final Object entityManager;
        
        public EntityManagerWrapper(Object entityManager) {
            this.entityManager = entityManager;
        }
        
        /**
         * 创建原生查询
         */
        public QueryWrapper createNativeQuery(String sql) {
            try {
                Object query = entityManager.getClass()
                        .getMethod("createNativeQuery", String.class)
                        .invoke(entityManager, sql);
                return new QueryWrapper(query);
            } catch (Exception e) {
                throw new RuntimeException("创建原生查询失败: " + sql, e);
            }
        }
        
        /**
         * 持久化实体
         */
        public void persist(Object entity) {
            try {
                entityManager.getClass()
                        .getMethod("persist", Object.class)
                        .invoke(entityManager, entity);
            } catch (Exception e) {
                throw new RuntimeException("持久化实体失败", e);
            }
        }
        
        /**
         * 合并实体
         */
        @SuppressWarnings("unchecked")
        public <T> T merge(T entity) {
            try {
                return (T) entityManager.getClass()
                        .getMethod("merge", Object.class)
                        .invoke(entityManager, entity);
            } catch (Exception e) {
                throw new RuntimeException("合并实体失败", e);
            }
        }
        
        /**
         * 删除实体
         */
        public void remove(Object entity) {
            try {
                entityManager.getClass()
                        .getMethod("remove", Object.class)
                        .invoke(entityManager, entity);
            } catch (Exception e) {
                throw new RuntimeException("删除实体失败", e);
            }
        }
        
        /**
         * 根据 ID 查找实体
         */
        @SuppressWarnings("unchecked")
        public <T> T find(Class<T> entityClass, Object id) {
            try {
                return (T) entityManager.getClass()
                        .getMethod("find", Class.class, Object.class)
                        .invoke(entityManager, entityClass, id);
            } catch (Exception e) {
                throw new RuntimeException("查找实体失败", e);
            }
        }
        
        /**
         * 获取底层连接
         */
        public Connection unwrap(Class<Connection> clazz) {
            try {
                return (Connection) entityManager.getClass()
                        .getMethod("unwrap", Class.class)
                        .invoke(entityManager, clazz);
            } catch (Exception e) {
                throw new RuntimeException("获取连接失败", e);
            }
        }
    }
    
    /**
     * EntityManagerFactory 包装类
     * 保留用于未来扩展（如需要创建新的 EntityManager 实例）
     */
    private static class EntityManagerFactoryWrapper {
        private final Object entityManagerFactory;
        
        public EntityManagerFactoryWrapper(Object entityManagerFactory) {
            this.entityManagerFactory = entityManagerFactory;
        }
        
        /**
         * 创建 EntityManager
         * 保留用于未来扩展
         */
        @SuppressWarnings("unused")
        public EntityManagerWrapper createEntityManager() {
            try {
                Object em = entityManagerFactory.getClass()
                        .getMethod("createEntityManager")
                        .invoke(entityManagerFactory);
                return new EntityManagerWrapper(em);
            } catch (Exception e) {
                throw new RuntimeException("创建 EntityManager 失败", e);
            }
        }
    }
    
    /**
     * Query 包装类
     */
    private static class QueryWrapper {
        private final Object query;
        
        public QueryWrapper(Object query) {
            this.query = query;
        }
        
        /**
         * 设置参数
         */
        public QueryWrapper setParameter(int position, Object value) {
            try {
                query.getClass()
                        .getMethod("setParameter", int.class, Object.class)
                        .invoke(query, position, value);
                return this;
            } catch (Exception e) {
                throw new RuntimeException("设置参数失败", e);
            }
        }
        
        /**
         * 执行更新
         */
        public int executeUpdate() {
            try {
                return (Integer) query.getClass()
                        .getMethod("executeUpdate")
                        .invoke(query);
            } catch (Exception e) {
                throw new RuntimeException("执行更新失败", e);
            }
        }
        
        /**
         * 获取单个结果
         */
        public Object getSingleResult() {
            try {
                return query.getClass()
                        .getMethod("getSingleResult")
                        .invoke(query);
            } catch (Exception e) {
                throw new RuntimeException("获取单个结果失败", e);
            }
        }
    }

    @Override
    public Connection getConnection() throws Exception {
        // JPA EntityManager 可以获取底层连接
        return entityManagerWrapper.unwrap(Connection.class);
    }
    
    @Override
    public ResultSet executeQuery(String sql, Object... params) throws Exception {
        // JPA 不直接返回 ResultSet，需要通过 Connection 执行
        Connection conn = getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            stmt.setObject(i + 1, params[i]);
        }
        return stmt.executeQuery();
    }
    
    @Override
    public int executeUpdate(String sql, Object... params) throws Exception {
        // 使用 JPA 原生 SQL
        QueryWrapper query = entityManagerWrapper.createNativeQuery(sql);
        for (int i = 0; i < params.length; i++) {
            query.setParameter(i + 1, params[i]);
        }
        return query.executeUpdate();
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
        QueryWrapper query = entityManagerWrapper.createNativeQuery("SELECT COUNT(*) FROM " + tableName);
        Object result = query.getSingleResult();
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
        // 使用原生 SQL 执行 DDL
        String ddl = buildCreateTableDDL(tableName, structure);
        QueryWrapper query = entityManagerWrapper.createNativeQuery(ddl);
        query.executeUpdate();
        log.info("创建表成功: {}", tableName);
    }
    
    @Override
    public void truncateTable(String tableName) throws Exception {
        QueryWrapper query = entityManagerWrapper.createNativeQuery("TRUNCATE TABLE " + tableName);
        query.executeUpdate();
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
        
        structure.setPrimaryKeys(getPrimaryKeysFromMetaData(conn, tableName));
        
        return structure.getColumns().isEmpty() ? null : structure;
    }
    
    /**
     * 从元数据获取主键
     */
    private List<String> getPrimaryKeysFromMetaData(Connection conn, String tableName) 
            throws Exception {
        List<String> primaryKeys = new ArrayList<>();
        DatabaseMetaData metaData = conn.getMetaData();
        String catalog = conn.getCatalog();
        String schema = conn.getSchema();
        
        try (ResultSet rs = metaData.getPrimaryKeys(catalog, schema, tableName)) {
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
     * 使用 JPA 保存实体
     */
    public <T> T save(T entity) throws Exception {
        entityManagerWrapper.persist(entity);
        return entity;
    }
    
    /**
     * 使用 JPA 更新实体
     */
    public <T> T update(T entity) throws Exception {
        return entityManagerWrapper.merge(entity);
    }
    
    /**
     * 使用 JPA 删除实体
     */
    public void delete(Object entity) throws Exception {
        entityManagerWrapper.remove(entity);
    }
    
    /**
     * 使用 JPA 根据 ID 查找实体
     */
    public <T> T findById(Class<T> entityClass, Object id) throws Exception {
        return entityManagerWrapper.find(entityClass, id);
    }
}

