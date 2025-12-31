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
 * JPA的操作过于繁琐，这里可以考虑不予实现
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
                // ========== 反射调用：JPA EntityManager.createNativeQuery() ==========
                // 类名：javax.persistence.EntityManager
                // 方法原型：Query createNativeQuery(String sqlString)
                // 作用：创建原生 SQL 查询对象，用于执行数据库特定的 SQL 语句
                // 参数：sql - 原生 SQL 语句（如 "SELECT * FROM table WHERE id = ?"）
                // 返回：Query 对象，用于设置参数和执行查询
                // 位置：JpaDatabaseOperator.java:83-85
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
                // ========== 反射调用：JPA EntityManager.persist() ==========
                // 类名：javax.persistence.EntityManager
                // 方法原型：void persist(Object entity)
                // 作用：将实体对象持久化到数据库（相当于 INSERT 操作）
                // 参数：entity - 要持久化的实体对象（必须标注 @Entity）
                // 说明：实体对象会被添加到持久化上下文中，在事务提交时写入数据库
                // 位置：JpaDatabaseOperator.java:97-99
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
                // ========== 反射调用：JPA EntityManager.merge() ==========
                // 类名：javax.persistence.EntityManager
                // 方法原型：<T> T merge(T entity)
                // 作用：合并实体对象到数据库（相当于 UPDATE 操作）
                // 参数：entity - 要合并的实体对象
                // 返回：合并后的实体对象（可能是新的托管实例）
                // 说明：如果实体已存在（根据主键），则更新；如果不存在，则插入
                // 位置：JpaDatabaseOperator.java:111-113
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
                // ========== 反射调用：JPA EntityManager.remove() ==========
                // 类名：javax.persistence.EntityManager
                // 方法原型：void remove(Object entity)
                // 作用：从数据库中删除实体对象（相当于 DELETE 操作）
                // 参数：entity - 要删除的实体对象（必须是托管实体）
                // 说明：实体对象必须处于托管状态，在事务提交时从数据库删除
                // 位置：JpaDatabaseOperator.java:124-126
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
                // ========== 反射调用：JPA EntityManager.find() ==========
                // 类名：javax.persistence.EntityManager
                // 方法原型：<T> T find(Class<T> entityClass, Object primaryKey)
                // 作用：根据主键查找实体对象（相当于 SELECT WHERE id = ?）
                // 参数：entityClass - 实体类，id - 主键值
                // 返回：找到的实体对象，如果不存在则返回 null
                // 位置：JpaDatabaseOperator.java:138-140
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
                // ========== 反射调用：JPA EntityManager.unwrap() ==========
                // 类名：javax.persistence.EntityManager
                // 方法原型：<T> T unwrap(Class<T> clazz)
                // 作用：获取 EntityManager 的底层实现对象（如 JDBC Connection）
                // 参数：clazz - 要获取的底层对象类型（如 Connection.class）
                // 返回：底层对象实例
                // 说明：用于获取 JPA 实现提供的底层资源（如 JDBC Connection、Hibernate Session 等）
                // 位置：JpaDatabaseOperator.java:151-153
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
                // ========== 反射调用：JPA EntityManagerFactory.createEntityManager() ==========
                // 类名：javax.persistence.EntityManagerFactory
                // 方法原型：EntityManager createEntityManager()
                // 作用：创建新的 EntityManager 实例
                // 返回：新创建的 EntityManager 实例
                // 说明：EntityManagerFactory 是 EntityManager 的工厂类，用于创建 EntityManager
                // 位置：JpaDatabaseOperator.java:178-180
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
                // ========== 反射调用：JPA Query.setParameter() ==========
                // 类名：javax.persistence.Query
                // 方法原型：Query setParameter(int position, Object value)
                // 作用：为 SQL 查询设置参数（按位置，从 1 开始）
                // 参数：position - 参数位置（从 1 开始），value - 参数值
                // 返回：Query 对象（支持链式调用）
                // 说明：用于设置 SQL 中的占位符参数（如 "SELECT * FROM table WHERE id = ?"）
                // 位置：JpaDatabaseOperator.java:203-205
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
                // ========== 反射调用：JPA Query.executeUpdate() ==========
                // 类名：javax.persistence.Query
                // 方法原型：int executeUpdate()
                // 作用：执行更新或删除 SQL 语句（UPDATE/DELETE）
                // 返回：受影响的行数
                // 说明：用于执行修改数据的 SQL 语句，不返回结果集
                // 位置：JpaDatabaseOperator.java:217-219
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
                // ========== 反射调用：JPA Query.getSingleResult() ==========
                // 类名：javax.persistence.Query
                // 方法原型：Object getSingleResult()
                // 作用：执行查询并返回单个结果对象
                // 返回：查询结果对象（如果查询返回多行会抛出异常）
                // 说明：用于执行返回单行结果的查询（如 COUNT、MAX、MIN 等聚合函数）
                // 位置：JpaDatabaseOperator.java:230-232
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

