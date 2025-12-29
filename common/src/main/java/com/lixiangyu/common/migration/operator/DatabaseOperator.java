package com.lixiangyu.common.migration.operator;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.List;

/**
 * 数据库操作接口
 * 支持多种数据库操作方式：JDBC、MyBatis、JdbcTemplate、JPA 等
 * 
 * @author lixiangyu
 */
public interface DatabaseOperator {
    
    /**
     * 获取数据库连接
     *
     * @return 数据库连接
     */
    Connection getConnection() throws Exception;
    
    /**
     * 执行查询
     *
     * @param sql SQL 语句
     * @param params 参数
     * @return 结果集
     */
    ResultSet executeQuery(String sql, Object... params) throws Exception;
    
    /**
     * 执行更新（INSERT/UPDATE/DELETE）
     *
     * @param sql SQL 语句
     * @param params 参数
     * @return 影响行数
     */
    int executeUpdate(String sql, Object... params) throws Exception;
    
    /**
     * 批量执行更新
     *
     * @param sql SQL 语句
     * @param batchParams 批量参数
     * @return 影响行数数组
     */
    int[] executeBatch(String sql, List<Object[]> batchParams) throws Exception;
    
    /**
     * 获取记录数
     *
     * @param tableName 表名
     * @return 记录数
     */
    long getRecordCount(String tableName) throws Exception;
    
    /**
     * 获取表结构
     *
     * @param tableName 表名
     * @return 表结构信息（列名、类型等）
     */
    TableStructure getTableStructure(String tableName) throws Exception;
    
    /**
     * 创建表
     *
     * @param tableName 表名
     * @param structure 表结构
     */
    void createTable(String tableName, TableStructure structure) throws Exception;
    
    /**
     * 清空表
     *
     * @param tableName 表名
     */
    void truncateTable(String tableName) throws Exception;
    
    /**
     * 获取主键列表
     *
     * @param tableName 表名
     * @return 主键列名列表
     */
    List<String> getPrimaryKeys(String tableName) throws Exception;
    
    /**
     * 表结构信息
     */
    class TableStructure {
        private String tableName;
        private List<TableColumn> columns;
        private List<String> primaryKeys;
        
        // Getters and Setters
        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public List<TableColumn> getColumns() { return columns; }
        public void setColumns(List<TableColumn> columns) { this.columns = columns; }
        public List<String> getPrimaryKeys() { return primaryKeys; }
        public void setPrimaryKeys(List<String> primaryKeys) { this.primaryKeys = primaryKeys; }
    }
    
    /**
     * 表列信息
     */
    class TableColumn {
        private String name;
        private int type;
        private String typeName;
        private int size;
        private boolean nullable;
        private String defaultValue;
        
        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getType() { return type; }
        public void setType(int type) { this.type = type; }
        public String getTypeName() { return typeName; }
        public void setTypeName(String typeName) { this.typeName = typeName; }
        public int getSize() { return size; }
        public void setSize(int size) { this.size = size; }
        public boolean isNullable() { return nullable; }
        public void setNullable(boolean nullable) { this.nullable = nullable; }
        public String getDefaultValue() { return defaultValue; }
        public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }
    }
}

