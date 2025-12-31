# Apache DbUtils 使用指南

## 文档信息

- **创建日期**: 2025-12-29
- **文档类型**: 技术文档
- **版本**: 1.0

---

## 一、Apache DbUtils 简介

### 1.1 什么是 Apache DbUtils？

Apache Commons DbUtils 是 Apache 软件基金会提供的一个轻量级 JDBC 工具类库，旨在简化 JDBC 编程，减少样板代码，并确保资源的正确释放。

### 1.2 主要特点

✅ **简化 JDBC 操作**：封装常见的数据库操作，减少重复代码  
✅ **自动资源管理**：自动关闭 Connection、Statement、ResultSet，防止资源泄漏  
✅ **结果集映射**：支持将 ResultSet 自动映射到 JavaBean、Map、List 等  
✅ **轻量级**：体积小，依赖少，易于集成  
✅ **线程安全**：QueryRunner 是线程安全的，可以在多线程环境中使用  

### 1.3 适用场景

- 需要简化 JDBC 操作的项目
- 不想引入重量级 ORM 框架（如 Hibernate、MyBatis）的项目
- 需要轻量级数据库操作工具的项目
- 需要快速开发数据库操作代码的场景

---

## 二、Maven 依赖

### 2.1 添加依赖

```xml
<dependency>
    <groupId>commons-dbutils</groupId>
    <artifactId>commons-dbutils</artifactId>
    <version>1.7</version>
</dependency>
```

### 2.2 版本说明

- **最新稳定版本**：1.7（截至 2025 年）
- **JDK 要求**：JDK 1.6+
- **依赖**：仅依赖 JDBC 驱动，无其他第三方依赖

---

## 三、核心类介绍

### 3.1 QueryRunner

`QueryRunner` 是 DbUtils 的核心类，用于执行 SQL 查询和更新操作。

#### 3.1.1 构造函数

```java
// 方式1：无参构造函数（需要手动传入 Connection）
QueryRunner runner = new QueryRunner();

// 方式2：传入 DataSource（自动管理连接）
QueryRunner runner = new QueryRunner(dataSource);
```

#### 3.1.2 主要方法

| 方法 | 说明 | 返回类型 |
|------|------|---------|
| `query(Connection, String, ResultSetHandler, Object...)` | 执行查询 | `T` |
| `query(String, ResultSetHandler, Object...)` | 执行查询（使用 DataSource） | `T` |
| `update(Connection, String, Object...)` | 执行更新/插入/删除 | `int` |
| `update(String, Object...)` | 执行更新（使用 DataSource） | `int` |
| `batch(Connection, String, Object[][])` | 批量执行 | `int[]` |
| `batch(String, Object[][])` | 批量执行（使用 DataSource） | `int[]` |
| `insert(Connection, String, ResultSetHandler, Object...)` | 执行插入并返回生成的主键 | `T` |
| `insert(String, ResultSetHandler, Object...)` | 执行插入（使用 DataSource） | `T` |

---

### 3.2 ResultSetHandler

`ResultSetHandler` 是接口，用于处理 ResultSet 并将结果转换为所需的对象。

#### 3.2.1 常用实现类

| 实现类 | 说明 | 返回类型 |
|--------|------|---------|
| `BeanHandler<T>` | 将单行结果映射到 JavaBean | `T` |
| `BeanListHandler<T>` | 将多行结果映射到 JavaBean 列表 | `List<T>` |
| `MapHandler` | 将单行结果映射到 Map | `Map<String, Object>` |
| `MapListHandler` | 将多行结果映射到 Map 列表 | `List<Map<String, Object>>` |
| `ScalarHandler<T>` | 获取单行单列的值 | `T` |
| `ColumnListHandler<T>` | 获取单列的值列表 | `List<T>` |
| `KeyedHandler<K>` | 将结果映射到 Map，以指定列为 key | `Map<K, Map<String, Object>>` |
| `ArrayHandler` | 将单行结果映射到 Object 数组 | `Object[]` |
| `ArrayListHandler` | 将多行结果映射到 Object 数组列表 | `List<Object[]>` |

---

### 3.3 DbUtils

`DbUtils` 是工具类，提供了一些静态方法用于资源管理和结果集处理。

#### 3.3.1 主要方法

```java
// 关闭 Connection
DbUtils.close(Connection conn);

// 关闭 Statement
DbUtils.close(Statement stmt);

// 关闭 ResultSet
DbUtils.close(ResultSet rs);

// 关闭所有资源
DbUtils.closeQuietly(Connection conn, Statement stmt, ResultSet rs);

// 提交并关闭连接
DbUtils.commitAndClose(Connection conn);

// 回滚并关闭连接
DbUtils.rollbackAndClose(Connection conn);
```

---

## 四、使用示例

### 4.1 基础查询示例

#### 4.1.1 查询单条记录（BeanHandler）

```java
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.BeanHandler;
import javax.sql.DataSource;
import java.sql.SQLException;

public class UserDao {
    private QueryRunner runner;
    private DataSource dataSource;
    
    public UserDao(DataSource dataSource) {
        this.dataSource = dataSource;
        this.runner = new QueryRunner(dataSource);
    }
    
    /**
     * 根据 ID 查询用户
     */
    public User findById(Long id) throws SQLException {
        String sql = "SELECT id, username, email, phone FROM t_user WHERE id = ?";
        return runner.query(sql, new BeanHandler<>(User.class), id);
    }
}
```

#### 4.1.2 查询多条记录（BeanListHandler）

```java
import org.apache.commons.dbutils.handlers.BeanListHandler;
import java.util.List;

/**
 * 查询所有用户
 */
public List<User> findAll() throws SQLException {
    String sql = "SELECT id, username, email, phone FROM t_user WHERE status = 1";
    return runner.query(sql, new BeanListHandler<>(User.class));
}

/**
 * 根据条件查询用户列表
 */
public List<User> findByAge(int minAge) throws SQLException {
    String sql = "SELECT id, username, email, phone FROM t_user WHERE age > ?";
    return runner.query(sql, new BeanListHandler<>(User.class), minAge);
}
```

#### 4.1.3 查询单行单列（ScalarHandler）

```java
import org.apache.commons.dbutils.handlers.ScalarHandler;

/**
 * 查询用户总数
 */
public Long count() throws SQLException {
    String sql = "SELECT COUNT(*) FROM t_user";
    return runner.query(sql, new ScalarHandler<>());
}

/**
 * 查询最大 ID
 */
public Long getMaxId() throws SQLException {
    String sql = "SELECT MAX(id) FROM t_user";
    return runner.query(sql, new ScalarHandler<>());
}
```

#### 4.1.4 查询单列列表（ColumnListHandler）

```java
import org.apache.commons.dbutils.handlers.ColumnListHandler;

/**
 * 查询所有用户名
 */
public List<String> findAllUsernames() throws SQLException {
    String sql = "SELECT username FROM t_user";
    return runner.query(sql, new ColumnListHandler<>());
}
```

#### 4.1.5 查询为 Map（MapHandler / MapListHandler）

```java
import org.apache.commons.dbutils.handlers.MapHandler;
import org.apache.commons.dbutils.handlers.MapListHandler;
import java.util.Map;
import java.util.List;

/**
 * 查询单行结果为 Map
 */
public Map<String, Object> findByIdAsMap(Long id) throws SQLException {
    String sql = "SELECT * FROM t_user WHERE id = ?";
    return runner.query(sql, new MapHandler(), id);
}

/**
 * 查询多行结果为 Map 列表
 */
public List<Map<String, Object>> findAllAsMap() throws SQLException {
    String sql = "SELECT * FROM t_user";
    return runner.query(sql, new MapListHandler());
}
```

---

### 4.2 更新操作示例

#### 4.2.1 插入数据

```java
/**
 * 插入用户
 */
public int insert(User user) throws SQLException {
    String sql = "INSERT INTO t_user (username, email, phone, create_time) VALUES (?, ?, ?, ?)";
    return runner.update(sql, 
        user.getUsername(), 
        user.getEmail(), 
        user.getPhone(), 
        new Date());
}

/**
 * 插入数据并返回生成的主键
 */
public Long insertAndGetId(User user) throws SQLException {
    String sql = "INSERT INTO t_user (username, email, phone, create_time) VALUES (?, ?, ?, ?)";
    ScalarHandler<Long> handler = new ScalarHandler<>();
    return runner.insert(sql, handler, 
        user.getUsername(), 
        user.getEmail(), 
        user.getPhone(), 
        new Date());
}
```

#### 4.2.2 更新数据

```java
/**
 * 更新用户
 */
public int update(User user) throws SQLException {
    String sql = "UPDATE t_user SET username = ?, email = ?, phone = ?, update_time = ? WHERE id = ?";
    return runner.update(sql, 
        user.getUsername(), 
        user.getEmail(), 
        user.getPhone(), 
        new Date(),
        user.getId());
}

/**
 * 根据条件更新
 */
public int updateStatus(Long id, Integer status) throws SQLException {
    String sql = "UPDATE t_user SET status = ?, update_time = ? WHERE id = ?";
    return runner.update(sql, status, new Date(), id);
}
```

#### 4.2.3 删除数据

```java
/**
 * 删除用户
 */
public int delete(Long id) throws SQLException {
    String sql = "DELETE FROM t_user WHERE id = ?";
    return runner.update(sql, id);
}
```

---

### 4.3 批量操作示例

#### 4.3.1 批量插入

```java
/**
 * 批量插入用户
 */
public int[] batchInsert(List<User> users) throws SQLException {
    String sql = "INSERT INTO t_user (username, email, phone, create_time) VALUES (?, ?, ?, ?)";
    
    // 准备批量参数
    Object[][] params = new Object[users.size()][];
    for (int i = 0; i < users.size(); i++) {
        User user = users.get(i);
        params[i] = new Object[]{
            user.getUsername(),
            user.getEmail(),
            user.getPhone(),
            new Date()
        };
    }
    
    return runner.batch(sql, params);
}
```

#### 4.3.2 批量更新

```java
/**
 * 批量更新用户状态
 */
public int[] batchUpdateStatus(List<Long> ids, Integer status) throws SQLException {
    String sql = "UPDATE t_user SET status = ?, update_time = ? WHERE id = ?";
    
    Object[][] params = new Object[ids.size()][];
    Date updateTime = new Date();
    for (int i = 0; i < ids.size(); i++) {
        params[i] = new Object[]{status, updateTime, ids.get(i)};
    }
    
    return runner.batch(sql, params);
}
```

---

### 4.4 事务处理示例

#### 4.4.1 手动管理事务

```java
import java.sql.Connection;

/**
 * 转账操作（事务示例）
 */
public void transfer(Long fromId, Long toId, BigDecimal amount) throws SQLException {
    Connection conn = null;
    try {
        // 获取连接
        conn = dataSource.getConnection();
        // 关闭自动提交
        conn.setAutoCommit(false);
        
        QueryRunner runner = new QueryRunner();
        
        // 扣款
        String deductSql = "UPDATE t_account SET balance = balance - ? WHERE id = ?";
        runner.update(conn, deductSql, amount, fromId);
        
        // 加款
        String addSql = "UPDATE t_account SET balance = balance + ? WHERE id = ?";
        runner.update(conn, addSql, amount, toId);
        
        // 提交事务
        conn.commit();
    } catch (SQLException e) {
        // 回滚事务
        if (conn != null) {
            conn.rollback();
        }
        throw e;
    } finally {
        // 关闭连接
        DbUtils.close(conn);
    }
}
```

#### 4.4.2 使用 DbUtils 工具类

```java
import org.apache.commons.dbutils.DbUtils;

/**
 * 转账操作（使用 DbUtils 工具类）
 */
public void transfer(Long fromId, Long toId, BigDecimal amount) throws SQLException {
    Connection conn = null;
    try {
        conn = dataSource.getConnection();
        conn.setAutoCommit(false);
        
        QueryRunner runner = new QueryRunner();
        
        String deductSql = "UPDATE t_account SET balance = balance - ? WHERE id = ?";
        runner.update(conn, deductSql, amount, fromId);
        
        String addSql = "UPDATE t_account SET balance = balance + ? WHERE id = ?";
        runner.update(conn, addSql, amount, toId);
        
        // 提交并关闭连接
        DbUtils.commitAndClose(conn);
    } catch (SQLException e) {
        // 回滚并关闭连接
        DbUtils.rollbackAndClose(conn);
        throw e;
    }
}
```

---

### 4.5 自定义 ResultSetHandler

#### 4.5.1 实现自定义 Handler

```java
import org.apache.commons.dbutils.ResultSetHandler;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 自定义 ResultSetHandler：将结果映射为 User 对象列表
 */
public class UserListHandler implements ResultSetHandler<List<User>> {
    @Override
    public List<User> handle(ResultSet rs) throws SQLException {
        List<User> users = new ArrayList<>();
        while (rs.next()) {
            User user = new User();
            user.setId(rs.getLong("id"));
            user.setUsername(rs.getString("username"));
            user.setEmail(rs.getString("email"));
            user.setPhone(rs.getString("phone"));
            user.setCreateTime(rs.getTimestamp("create_time"));
            users.add(user);
        }
        return users;
    }
}

// 使用自定义 Handler
public List<User> findUsers() throws SQLException {
    String sql = "SELECT * FROM t_user";
    return runner.query(sql, new UserListHandler());
}
```

#### 4.5.2 使用 Lambda 表达式（Java 8+）

```java
import org.apache.commons.dbutils.ResultSetHandler;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 使用 Lambda 表达式创建自定义 Handler
 */
public List<User> findUsersWithLambda() throws SQLException {
    String sql = "SELECT * FROM t_user";
    
    ResultSetHandler<List<User>> handler = (ResultSet rs) -> {
        List<User> users = new ArrayList<>();
        while (rs.next()) {
            User user = new User();
            user.setId(rs.getLong("id"));
            user.setUsername(rs.getString("username"));
            user.setEmail(rs.getString("email"));
            users.add(user);
        }
        return users;
    };
    
    return runner.query(sql, handler);
}
```

---

## 五、与项目现有代码的对比

### 5.1 与 JDBC 原生代码对比

#### 5.1.1 JDBC 原生代码

```java
// JDBC 原生代码（繁琐、容易出错）
public User findById(Long id) throws SQLException {
    Connection conn = null;
    PreparedStatement stmt = null;
    ResultSet rs = null;
    try {
        conn = dataSource.getConnection();
        String sql = "SELECT * FROM t_user WHERE id = ?";
        stmt = conn.prepareStatement(sql);
        stmt.setLong(1, id);
        rs = stmt.executeQuery();
        
        User user = null;
        if (rs.next()) {
            user = new User();
            user.setId(rs.getLong("id"));
            user.setUsername(rs.getString("username"));
            user.setEmail(rs.getString("email"));
            // ... 更多字段
        }
        return user;
    } finally {
        // 手动关闭资源（容易遗漏）
        if (rs != null) rs.close();
        if (stmt != null) stmt.close();
        if (conn != null) conn.close();
    }
}
```

#### 5.1.2 DbUtils 代码

```java
// DbUtils 代码（简洁、安全）
public User findById(Long id) throws SQLException {
    String sql = "SELECT * FROM t_user WHERE id = ?";
    return runner.query(sql, new BeanHandler<>(User.class), id);
}
```

**优势**：
- ✅ 代码量减少 70%+
- ✅ 自动资源管理，不会泄漏
- ✅ 自动映射，无需手动设置字段
- ✅ 更易维护和阅读

---

### 5.2 与 MyBatis 对比

| 特性 | DbUtils | MyBatis |
|------|---------|---------|
| **学习成本** | 低 | 中 |
| **配置复杂度** | 低（无需配置） | 中（需要 XML/注解配置） |
| **功能丰富度** | 基础 CRUD | 丰富（动态 SQL、缓存、插件等） |
| **性能** | 良好 | 优秀 |
| **适用场景** | 简单项目、快速开发 | 复杂项目、企业级应用 |

**选择建议**：
- 简单项目、快速原型：使用 DbUtils
- 复杂项目、需要高级特性：使用 MyBatis

---

## 六、最佳实践

### 6.1 使用 DataSource 管理连接

```java
// ✅ 推荐：使用 DataSource
QueryRunner runner = new QueryRunner(dataSource);

// ❌ 不推荐：手动管理 Connection
QueryRunner runner = new QueryRunner();
Connection conn = dataSource.getConnection();
// ... 需要手动关闭连接
```

### 6.2 使用参数化查询防止 SQL 注入

```java
// ✅ 推荐：使用参数化查询
String sql = "SELECT * FROM t_user WHERE username = ?";
runner.query(sql, new BeanHandler<>(User.class), username);

// ❌ 不推荐：字符串拼接（存在 SQL 注入风险）
String sql = "SELECT * FROM t_user WHERE username = '" + username + "'";
```

### 6.3 合理使用 ResultSetHandler

```java
// ✅ 推荐：使用合适的 Handler
// 查询单条记录
runner.query(sql, new BeanHandler<>(User.class), id);

// 查询多条记录
runner.query(sql, new BeanListHandler<>(User.class));

// 查询单行单列
runner.query(sql, new ScalarHandler<>());

// ❌ 不推荐：使用不合适的 Handler
// 查询多条记录却使用 BeanHandler（只会返回第一条）
runner.query(sql, new BeanHandler<>(User.class));
```

### 6.4 异常处理

```java
// ✅ 推荐：正确处理异常
public User findById(Long id) {
    try {
        String sql = "SELECT * FROM t_user WHERE id = ?";
        return runner.query(sql, new BeanHandler<>(User.class), id);
    } catch (SQLException e) {
        log.error("查询用户失败，ID: {}", id, e);
        throw new RuntimeException("查询用户失败", e);
    }
}

// ❌ 不推荐：吞掉异常
public User findById(Long id) {
    try {
        // ...
    } catch (SQLException e) {
        // 吞掉异常，难以排查问题
        return null;
    }
}
```

### 6.5 批量操作优化

```java
// ✅ 推荐：使用批量操作
public int[] batchInsert(List<User> users) throws SQLException {
    String sql = "INSERT INTO t_user (username, email) VALUES (?, ?)";
    Object[][] params = users.stream()
        .map(user -> new Object[]{user.getUsername(), user.getEmail()})
        .toArray(Object[][]::new);
    return runner.batch(sql, params);
}

// ❌ 不推荐：循环单条插入（性能差）
public void batchInsert(List<User> users) throws SQLException {
    for (User user : users) {
        runner.update("INSERT INTO t_user (username, email) VALUES (?, ?)", 
            user.getUsername(), user.getEmail());
    }
}
```

---

## 七、常见问题

### 7.1 Bean 映射失败

**问题**：查询结果无法映射到 JavaBean

**原因**：
- 数据库字段名与 JavaBean 属性名不匹配（如 `user_name` vs `userName`）
- JavaBean 缺少无参构造函数
- JavaBean 属性缺少 setter 方法

**解决方案**：

```java
// 方案1：使用别名
String sql = "SELECT id, username AS userName, email FROM t_user";

// 方案2：使用 MapHandler 然后手动转换
Map<String, Object> map = runner.query(sql, new MapHandler(), id);
User user = convertMapToUser(map);

// 方案3：自定义 ResultSetHandler
ResultSetHandler<User> handler = (ResultSet rs) -> {
    if (rs.next()) {
        User user = new User();
        user.setId(rs.getLong("id"));
        user.setUsername(rs.getString("username"));
        // ...
        return user;
    }
    return null;
};
```

---

### 7.2 日期时间处理

**问题**：日期时间字段映射不正确

**解决方案**：

```java
// 方案1：在 SQL 中使用 DATE_FORMAT
String sql = "SELECT DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS createTime FROM t_user";

// 方案2：自定义 ResultSetHandler 手动转换
ResultSetHandler<User> handler = (ResultSet rs) -> {
    User user = new User();
    Timestamp timestamp = rs.getTimestamp("create_time");
    if (timestamp != null) {
        user.setCreateTime(new Date(timestamp.getTime()));
    }
    return user;
};

// 方案3：使用 Java 8 的 LocalDateTime
// 需要在 JavaBean 中使用 LocalDateTime 类型
```

---

### 7.3 处理 NULL 值

**问题**：查询结果中的 NULL 值处理

**解决方案**：

```java
// 方案1：在 SQL 中使用 COALESCE
String sql = "SELECT COALESCE(phone, '') AS phone FROM t_user";

// 方案2：在 ResultSetHandler 中处理
ResultSetHandler<User> handler = (ResultSet rs) -> {
    User user = new User();
    String phone = rs.getString("phone");
    user.setPhone(phone != null ? phone : ""); // 或使用 Optional
    return user;
};
```

---

## 八、性能优化建议

### 8.1 使用连接池

```java
// ✅ 推荐：使用连接池（如 HikariCP、Druid）
HikariConfig config = new HikariConfig();
config.setJdbcUrl("jdbc:mysql://localhost:3306/test");
config.setUsername("root");
config.setPassword("password");
HikariDataSource dataSource = new HikariDataSource(config);
QueryRunner runner = new QueryRunner(dataSource);
```

### 8.2 批量操作

```java
// ✅ 推荐：使用批量操作而不是循环单条操作
int[] results = runner.batch(sql, params);
```

### 8.3 合理使用索引

```java
// ✅ 推荐：在 WHERE 条件中使用索引字段
String sql = "SELECT * FROM t_user WHERE id = ?"; // id 是主键，有索引

// ❌ 不推荐：在非索引字段上查询
String sql = "SELECT * FROM t_user WHERE email = ?"; // 如果 email 没有索引
```

---

## 九、总结

### 9.1 优势

✅ **轻量级**：体积小，依赖少  
✅ **简单易用**：API 简洁，学习成本低  
✅ **自动资源管理**：不会泄漏资源  
✅ **结果集映射**：支持多种映射方式  
✅ **线程安全**：QueryRunner 是线程安全的  

### 9.2 适用场景

✅ 简单项目或快速原型开发  
✅ 不想引入重量级 ORM 框架  
✅ 需要轻量级数据库操作工具  
✅ 对性能要求不是特别高的场景  

### 9.3 不适用场景

❌ 需要复杂动态 SQL 的场景（建议使用 MyBatis）  
❌ 需要对象关系映射的场景（建议使用 Hibernate/JPA）  
❌ 需要高级缓存、插件等特性的场景（建议使用 MyBatis）  

---

## 十、参考资源

- **官方网站**：https://commons.apache.org/proper/commons-dbutils/
- **GitHub**：https://github.com/apache/commons-dbutils
- **API 文档**：https://commons.apache.org/proper/commons-dbutils/apidocs/
- **Maven 仓库**：https://mvnrepository.com/artifact/commons-dbutils/commons-dbutils

---

**文档版本**: 1.0  
**最后更新**: 2025-12-29

