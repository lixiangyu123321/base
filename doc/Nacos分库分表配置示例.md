# Nacos 分库分表配置示例

## 文档信息

- **创建日期**: 2025-12-29
- **配置中心**: Nacos
- **版本**: 1.0

---

## 一、配置说明

所有分库分表配置都存储在 Nacos 配置中心，支持动态更新。

### 1.1 配置项列表

| 配置项 | Data ID | Group | 说明 |
|--------|---------|-------|------|
| **数据源配置** | `sharding.config` | `DEFAULT_GROUP` | 数据源连接信息 |
| **分片规则** | `sharding.rules` | `DEFAULT_GROUP` | 分库分表规则和算法 |
| **路由规则** | `traffic.routing.config` | `DEFAULT_GROUP` | 流量路由规则 |

---

## 二、数据源配置

### 2.1 配置示例

**Data ID**: `sharding.config`  
**Group**: `DEFAULT_GROUP`  
**配置格式**: JSON

```json
{
  "dataSources": {
    "ds0": {
      "url": "jdbc:mysql://localhost:3307/db0?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true",
      "username": "root",
      "password": "123456",
      "driverClassName": "com.mysql.cj.jdbc.Driver",
      "minimumIdle": 5,
      "maximumPoolSize": 20,
      "connectionTimeout": 30000,
      "idleTimeout": 600000,
      "maxLifetime": 1800000
    },
    "ds1": {
      "url": "jdbc:mysql://localhost:3307/db1?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true",
      "username": "root",
      "password": "123456",
      "driverClassName": "com.mysql.cj.jdbc.Driver",
      "minimumIdle": 5,
      "maximumPoolSize": 20,
      "connectionTimeout": 30000,
      "idleTimeout": 600000,
      "maxLifetime": 1800000
    },
    "ds-slave": {
      "url": "jdbc:mysql://localhost:3308/db0?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true",
      "username": "root",
      "password": "123456",
      "driverClassName": "com.mysql.cj.jdbc.Driver",
      "minimumIdle": 5,
      "maximumPoolSize": 20,
      "connectionTimeout": 30000
    }
  }
}
```

### 2.2 配置说明

- `dataSources`：数据源映射，Key 为数据源名称，Value 为数据源配置
- `url`：数据库连接 URL
- `username`：数据库用户名
- `password`：数据库密码
- `driverClassName`：JDBC 驱动类名
- `minimumIdle`：最小空闲连接数
- `maximumPoolSize`：最大连接池大小
- `connectionTimeout`：获取连接的超时时间（毫秒）
- `idleTimeout`：连接的空闲超时时间（毫秒）
- `maxLifetime`：连接的最大生命周期（毫秒）

---

## 三、分片规则配置

### 3.1 配置示例

**Data ID**: `sharding.rules`  
**Group**: `DEFAULT_GROUP`  
**配置格式**: JSON

```json
{
  "tables": {
    "t_user": {
      "actualDataNodes": "ds$->{0..1}.t_user_$->{0..3}",
      "databaseStrategy": {
        "shardingColumn": "user_id",
        "shardingAlgorithmName": "database-inline"
      },
      "tableStrategy": {
        "shardingColumn": "user_id",
        "shardingAlgorithmName": "table-inline"
      }
    },
    "t_order": {
      "actualDataNodes": "ds$->{0..1}.t_order_$->{0..7}",
      "databaseStrategy": {
        "shardingColumn": "user_id",
        "shardingAlgorithmName": "database-inline"
      },
      "tableStrategy": {
        "shardingColumn": "order_id",
        "shardingAlgorithmName": "table-inline-order"
      }
    }
  },
  "defaultDatabaseStrategy": {
    "shardingColumn": "user_id",
    "shardingAlgorithmName": "database-inline"
  },
  "defaultTableStrategy": {
    "shardingColumn": "id",
    "shardingAlgorithmName": "table-inline"
  },
  "algorithms": {
    "database-inline": {
      "type": "INLINE",
      "properties": {
        "algorithm-expression": "ds$->{user_id % 2}"
      }
    },
    "table-inline": {
      "type": "INLINE",
      "properties": {
        "algorithm-expression": "t_user_$->{user_id % 4}"
      }
    },
    "table-inline-order": {
      "type": "INLINE",
      "properties": {
        "algorithm-expression": "t_order_$->{order_id % 8}"
      }
    }
  }
}
```

### 3.2 配置说明

#### 3.2.1 表规则（tables）

- `actualDataNodes`：实际数据节点，格式：`数据源名.表名`
  - 示例：`ds$->{0..1}.t_user_$->{0..3}` 表示：
    - 数据源：ds0, ds1
    - 表：t_user_0, t_user_1, t_user_2, t_user_3
    - 总共：2 个库 × 4 个表 = 8 个分片

- `databaseStrategy`：数据库分片策略
  - `shardingColumn`：分片列（用于分库的字段）
  - `shardingAlgorithmName`：分片算法名称

- `tableStrategy`：表分片策略
  - `shardingColumn`：分片列（用于分表的字段）
  - `shardingAlgorithmName`：分片算法名称

#### 3.2.2 默认策略

- `defaultDatabaseStrategy`：默认数据库分片策略
- `defaultTableStrategy`：默认表分片策略

#### 3.2.3 分片算法（algorithms）

- `type`：算法类型
  - `INLINE`：行表达式分片算法
  - `MOD`：取模分片算法
  - `HASH_MOD`：哈希取模分片算法
  - `CLASS_BASED`：自定义分片算法

- `properties`：算法属性
  - `algorithm-expression`：行表达式（用于 INLINE 算法）
    - 示例：`ds$->{user_id % 2}` 表示根据 user_id 取模 2 选择数据源

### 3.3 分片算法示例

#### 取模分片（INLINE）

```json
{
  "type": "INLINE",
  "properties": {
    "algorithm-expression": "ds$->{user_id % 2}"
  }
}
```

#### 范围分片（CLASS_BASED）

```json
{
  "type": "CLASS_BASED",
  "properties": {
    "strategy": "STANDARD",
    "algorithmClassName": "com.lixiangyu.common.sharding.algorithm.RangeShardingAlgorithm"
  }
}
```

---

## 四、流量路由配置

### 4.1 配置示例

**Data ID**: `traffic.routing.config`  
**Group**: `DEFAULT_GROUP`  
**配置格式**: JSON

```json
{
  "rules": [
    {
      "name": "read-slave",
      "targetDataSource": "ds-slave",
      "priority": 1,
      "sqlTypes": ["SELECT"],
      "tableNames": ["t_user", "t_order"]
    },
    {
      "name": "write-master",
      "targetDataSource": "ds0",
      "priority": 2,
      "sqlTypes": ["INSERT", "UPDATE", "DELETE"]
    },
    {
      "name": "user-table-route",
      "targetDataSource": "ds-user",
      "priority": 3,
      "tablePatterns": ["t_user_*"]
    }
  ],
  "defaultRule": {
    "name": "default",
    "targetDataSource": "ds0",
    "priority": 999
  }
}
```

### 4.2 配置说明

#### 4.2.1 路由规则（rules）

- `name`：规则名称（唯一标识）
- `targetDataSource`：目标数据源名称
- `priority`：优先级（数字越小优先级越高）
- `sqlTypes`：匹配的 SQL 类型
  - `SELECT`：查询
  - `INSERT`：插入
  - `UPDATE`：更新
  - `DELETE`：删除
- `tableNames`：匹配的表名列表（精确匹配）
- `tablePatterns`：匹配的表名模式（支持通配符）
  - `*`：匹配任意字符
  - `?`：匹配单个字符
  - 示例：`t_user_*` 匹配 `t_user_0`, `t_user_1` 等

#### 4.2.2 默认规则（defaultRule）

当所有规则都不匹配时，使用默认规则。

### 4.3 路由规则示例

#### 读写分离

```json
{
  "name": "read-slave",
  "targetDataSource": "ds-slave",
  "priority": 1,
  "sqlTypes": ["SELECT"]
}
```

#### 表路由

```json
{
  "name": "user-table-route",
  "targetDataSource": "ds-user",
  "priority": 3,
  "tablePatterns": ["t_user_*"]
}
```

---

## 五、配置步骤

### 5.1 在 Nacos 中创建配置

1. **登录 Nacos 控制台**
   - 地址：http://localhost:8848/nacos
   - 用户名/密码：nacos / nacos

2. **创建数据源配置**
   - 命名空间：选择对应的命名空间（如 dev、test、prod）
   - Data ID：`sharding.config`
   - Group：`DEFAULT_GROUP`
   - 配置格式：JSON
   - 配置内容：参考"二、数据源配置"

3. **创建分片规则配置**
   - Data ID：`sharding.rules`
   - Group：`DEFAULT_GROUP`
   - 配置格式：JSON
   - 配置内容：参考"三、分片规则配置"

4. **创建路由规则配置**
   - Data ID：`traffic.routing.config`
   - Group：`DEFAULT_GROUP`
   - 配置格式：JSON
   - 配置内容：参考"四、流量路由配置"

### 5.2 启用分库分表

**application.yml**：

```yaml
sharding:
  enabled: true
  sql-show: true
```

### 5.3 验证配置

1. **启动应用**，查看日志确认配置加载成功
2. **执行 SQL**，查看路由是否正确
3. **修改 Nacos 配置**，验证热更新是否生效

---

## 六、配置验证

### 6.1 检查配置加载

查看应用日志，确认以下信息：
- ✅ "ShardingSphere 数据源初始化完成"
- ✅ "成功加载分库分表配置"
- ✅ "成功加载流量路由配置"

### 6.2 测试分片路由

```sql
-- 测试分库分表路由
SELECT * FROM t_user WHERE user_id = 1;
-- 应该路由到：ds1.t_user_1

SELECT * FROM t_user WHERE user_id = 2;
-- 应该路由到：ds0.t_user_2
```

### 6.3 测试读写分离

```sql
-- 读请求应该路由到从库
SELECT * FROM t_user;

-- 写请求应该路由到主库
INSERT INTO t_user (user_id, username) VALUES (1, 'test');
```

---

## 七、常见问题

### Q1: 配置更新后不生效？

**A**: 
1. 检查 Nacos 配置是否正确发布
2. 检查应用日志，确认配置变更监听器是否触发
3. 检查数据源是否重新初始化

### Q2: 如何查看当前路由规则？

**A**: 
- 查看应用日志（启用 `sql-show: true`）
- 使用 ShardingSphere 的管理接口（如果启用）

### Q3: 配置格式错误怎么办？

**A**: 
1. 检查 JSON 格式是否正确
2. 检查配置项名称是否正确
3. 查看应用日志中的错误信息

---

**文档版本**: 1.0  
**最后更新**: 2025-12-29

