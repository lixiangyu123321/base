package com.lixiangyu.common.migration.operator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.List;

/**
 * MyBatis 实现
 * 使用 MyBatis SqlSession 操作数据库
 * 
 * 注意：需要项目中有 MyBatis 配置
 * TODO 使用MyBatis对应的接口进行操作
 * @author lixiangyu
 */
@Slf4j
public class MyBatisDatabaseOperator implements DatabaseOperator {
    
    /**
     * SqlSessionTemplate 包装类
     * 使用包装类避免直接依赖 MyBatis 类型，同时保持类型安全
     */
    private final SqlSessionTemplateWrapper sqlSessionTemplateWrapper;
    
    /**
     * SqlSessionFactory 包装类
     * 保留用于未来扩展
     */
    @SuppressWarnings("unused")
    private final SqlSessionFactoryWrapper sqlSessionFactoryWrapper;
    
    /**
     * JdbcTemplate（用于降级操作）
     */
    private final JdbcTemplate jdbcTemplate;
    
    /**
     * 数据源
     */
    private final DataSource dataSource;
    
    /**
     * 构造函数
     * 
     * @param sqlSessionFactory SqlSessionFactory 实例（通过反射获取）
     * @param sqlSessionTemplate SqlSessionTemplate 实例（通过反射获取）
     * @param dataSource 数据源
     */
    public MyBatisDatabaseOperator(Object sqlSessionFactory, 
                                   Object sqlSessionTemplate,
                                   DataSource dataSource) {
        if (sqlSessionTemplate == null) {
            throw new IllegalArgumentException("SqlSessionTemplate 不能为 null");
        }
        if (dataSource == null) {
            throw new IllegalArgumentException("DataSource 不能为 null");
        }
        this.sqlSessionTemplateWrapper = new SqlSessionTemplateWrapper(sqlSessionTemplate);
        this.sqlSessionFactoryWrapper = new SqlSessionFactoryWrapper(sqlSessionFactory);
        this.dataSource = dataSource;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }
    
    /**
     * SqlSessionTemplate 包装类
     * 封装反射调用，提供类型安全的接口
     */
    private static class SqlSessionTemplateWrapper {
        private final Object sqlSessionTemplate;
        
        public SqlSessionTemplateWrapper(Object sqlSessionTemplate) {
            this.sqlSessionTemplate = sqlSessionTemplate;
        }
        
        /**
         * 获取数据库连接
         */
        public Connection getConnection() {
            try {
                // ========== 反射调用：MyBatis SqlSessionTemplate.getConnection() ==========
                // 类名：org.mybatis.spring.SqlSessionTemplate
                // 方法原型：Connection getConnection()
                // 作用：从 SqlSessionTemplate 获取底层 JDBC Connection
                // 返回：JDBC Connection 对象
                // 说明：SqlSessionTemplate 是 MyBatis 与 Spring 集成的会话模板，封装了 SqlSession
                // 位置：MyBatisDatabaseOperator.java:83-85
                return (Connection) sqlSessionTemplate.getClass()
                        .getMethod("getConnection")
                        .invoke(sqlSessionTemplate);
            } catch (Exception e) {
                throw new RuntimeException("获取连接失败", e);
            }
        }
        
        /**
         * 获取 SqlSession
         */
        @SuppressWarnings("unused")
        public SqlSessionWrapper getSqlSession() {
            try {
                // ========== 反射调用：MyBatis SqlSessionTemplate.getSqlSession() ==========
                // 类名：org.mybatis.spring.SqlSessionTemplate
                // 方法原型：SqlSession getSqlSession()
                // 作用：获取 SqlSessionTemplate 内部的 SqlSession 实例
                // 返回：SqlSession 对象（MyBatis 的核心会话对象）
                // 说明：SqlSession 是 MyBatis 的核心接口，用于执行 SQL 操作
                // 位置：MyBatisDatabaseOperator.java:97-99
                Object sqlSession = sqlSessionTemplate.getClass()
                        .getMethod("getSqlSession")
                        .invoke(sqlSessionTemplate);
                return new SqlSessionWrapper(sqlSession);
            } catch (Exception e) {
                throw new RuntimeException("获取 SqlSession 失败", e);
            }
        }
    }
    
    /**
     * SqlSessionFactory 包装类
     * 保留用于未来扩展
     */
    private static class SqlSessionFactoryWrapper {
        private final Object sqlSessionFactory;
        
        public SqlSessionFactoryWrapper(Object sqlSessionFactory) {
            this.sqlSessionFactory = sqlSessionFactory;
        }
        
        /**
         * 打开 SqlSession
         */
        @SuppressWarnings("unused")
        public SqlSessionWrapper openSession() {
            try {
                // ========== 反射调用：MyBatis SqlSessionFactory.openSession() ==========
                // 类名：org.apache.ibatis.session.SqlSessionFactory
                // 方法原型：SqlSession openSession()
                // 作用：创建新的 SqlSession 实例
                // 返回：新创建的 SqlSession 实例
                // 说明：SqlSessionFactory 是 SqlSession 的工厂类，用于创建 SqlSession
                // 位置：MyBatisDatabaseOperator.java:124-126
                Object sqlSession = sqlSessionFactory.getClass()
                        .getMethod("openSession")
                        .invoke(sqlSessionFactory);
                return new SqlSessionWrapper(sqlSession);
            } catch (Exception e) {
                throw new RuntimeException("打开 SqlSession 失败", e);
            }
        }
    }
    
    /**
     * SqlSession 包装类
     */
    private static class SqlSessionWrapper {
        private final Object sqlSession;
        
        public SqlSessionWrapper(Object sqlSession) {
            this.sqlSession = sqlSession;
        }
        
        /**
         * 执行查询
         */
        @SuppressWarnings("unused")
        public <T> List<T> selectList(String statement, Object parameter) {
            try {
                // ========== 反射调用：MyBatis SqlSession.selectList() ==========
                // 类名：org.apache.ibatis.session.SqlSession
                // 方法原型：<E> List<E> selectList(String statement, Object parameter)
                // 作用：执行查询语句并返回结果列表
                // 参数：statement - Mapper 方法全限定名或 SQL 语句 ID，parameter - 查询参数
                // 返回：查询结果列表
                // 说明：用于执行返回多行结果的查询（如 SELECT * FROM table）
                // 位置：MyBatisDatabaseOperator.java:151-153
                @SuppressWarnings("unchecked")
                List<T> result = (List<T>) sqlSession.getClass()
                        .getMethod("selectList", String.class, Object.class)
                        .invoke(sqlSession, statement, parameter);
                return result;
            } catch (Exception e) {
                throw new RuntimeException("执行查询失败", e);
            }
        }
        
        /**
         * 执行更新
         */
        @SuppressWarnings("unused")
        public int update(String statement, Object parameter) {
            try {
                // ========== 反射调用：MyBatis SqlSession.update() ==========
                // 类名：org.apache.ibatis.session.SqlSession
                // 方法原型：int update(String statement, Object parameter)
                // 作用：执行更新、插入或删除语句
                // 参数：statement - Mapper 方法全限定名或 SQL 语句 ID，parameter - 更新参数
                // 返回：受影响的行数
                // 说明：用于执行 INSERT、UPDATE、DELETE 等修改数据的 SQL 语句
                // 位置：MyBatisDatabaseOperator.java:166-168
                return (Integer) sqlSession.getClass()
                        .getMethod("update", String.class, Object.class)
                        .invoke(sqlSession, statement, parameter);
            } catch (Exception e) {
                throw new RuntimeException("执行更新失败", e);
            }
        }
    }
    
    @Override
    public Connection getConnection() throws Exception {
        // 使用 SqlSessionTemplate 获取连接
        try {
            return sqlSessionTemplateWrapper.getConnection();
        } catch (Exception e) {
            // 降级使用 DataSource
            log.warn("从 SqlSessionTemplate 获取连接失败，降级使用 DataSource", e);
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

