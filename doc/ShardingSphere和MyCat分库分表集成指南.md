# ShardingSphere 和 MyCat 分库分表集成指南

## 文档信息

- **创建日期**: 2025-12-29
- **功能模块**: 分库分表、流量路由
- **技术栈**: ShardingSphere-JDBC、MyCat、Nacos 配置中心
- **版本**: 1.0

---

## 一、概述

### 1.1 什么是分库分表？

**分库分表**是解决数据库性能瓶颈的常用方案：

- **分库**：将数据分散到多个数据库中，减少单库压力
- **分表**：将数据分散到多个表中，减少单表数据量
- **分片**：按照某种规则（如用户ID、时间等）将数据分散到不同的库或表中

### 1.2 ShardingSphere vs MyCat

| 特性 | ShardingSphere-JDBC | MyCat |
|------|---------------------|-------|
| **架构** | 客户端分片（JDBC 驱动） | 服务端分片（中间件） |
| **部署** | 应用内嵌，无需独立部署 | 需要独立部署中间件 |
| **性能** | 无网络开销，性能好 | 有网络开销，性能略低 |
| **复杂度** | 配置相对简单 | 配置相对复杂 |
| **适用场景** | 中小型项目，分片逻辑简单 | 大型项目，需要复杂路由规则 |

### 1.3 项目集成方案

本项目采用**混合方案**：
- **ShardingSphere-JDBC**：作为主要分库分表方案（客户端分片）
- **MyCat**：作为可选方案（服务端分片，适合复杂场景）
- **Nacos 配置中心**：统一管理分库分表规则，支持动态配置

---

## 二、ShardingSphere-JDBC 集成

### 2.1 依赖配置

**pom.xml**：

```xml
<!-- ShardingSphere-JDBC 分库分表 -->
<dependency>
    <groupId>org.apache.shardingsphere</groupId>
    <artifactId>shardingsphere-jdbc-core-spring-boot-starter</artifactId>
    <version>5.4.1</version>
</dependency>
```

### 2.2 配置类说明

#### 2.2.1 ShardingDataSourceConfig

**位置**：`common/src/main/java/com/lixiangyu/common/sharding/ShardingDataSourceConfig.java`

**功能**：
- 创建 ShardingSphere 数据源
- 从 Nacos 配置中心加载分库分表规则
- 支持配置热更新

**关键方法**：
- `loadShardingConfig()`：从 Nacos 加载配置
- `createShardingDataSource()`：创建 ShardingSphere 数据源
- `registerConfigListener()`：注册配置变更监听器

#### 2.2.2 ShardingRuleManager

**位置**：`common/src/main/java/com/lixiangyu/common/sharding/ShardingRuleManager.java`

**功能**：
- 管理分库分表规则
- 管理分片算法配置
- 支持规则动态更新

### 2.3 Nacos 配置格式

#### 2.3.1 数据源配置

**Data ID**: `sharding.config`

**配置格式**（JSON）：

```json
{
  "dataSources": {
    "ds0": {
      "url": "jdbc:mysql://localhost:3307/db0?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai",
      "username": "root",
      "password": "123456",
      "driverClassName": "com.mysql.cj.jdbc.Driver",
      "minimumIdle": 5,
      "maximumPoolSize": 20,
      "connectionTimeout": 30000
    },
    "ds1": {
      "url": "jdbc:mysql://localhost:3307/db1?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai",
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

#### 2.3.2 分片规则配置

**Data ID**: `sharding.rules`

**配置格式**（JSON）：

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
    }
  },
  "defaultDatabaseStrategy": {
    "shardingColumn": "user_id",
    "shardingAlgorithmName": "database-inline"
  },
  "defaultTableStrategy": {
    "shardingColumn": "user_id",
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
    }
  }
}
```

**配置说明**：
- `actualDataNodes`：实际数据节点，格式：`数据源名.表名`
- `databaseStrategy`：数据库分片策略
- `tableStrategy`：表分片策略
- `shardingColumn`：分片列
- `shardingAlgorithmName`：分片算法名称
- `algorithms`：分片算法配置

### 2.4 启用 ShardingSphere

**application.yml**：

```yaml
sharding:
  enabled: true
  sql-show: true
```

---

## 三、MyCat 集成

### 3.1 MyCat 简介

**MyCat** 是一个数据库中间件，提供：
- 分库分表路由
- 读写分离
- 数据分片
- SQL 拦截和改写

### 3.2 MyCat 部署

#### 3.2.1 下载 MyCat

```bash
# 下载 MyCat 2.x
wget https://github.com/MyCATApache/Mycat2/releases/download/2.0-beta-20220830/mycat2-2.0-beta-20220830.jar
```

#### 3.2.2 配置 MyCat

**server.xml**（MyCat 服务器配置）：

```xml
<server>
    <property name="serverPort">8066</property>
    <property name="managerPort">9066</property>
</server>
```

**schema.xml**（分片规则配置）：

```xml
<schema name="test_db" checkSQLschema="true" sqlMaxLimit="100">
    <table name="t_user" dataNode="dn1,dn2" rule="mod-long">
        <childTable name="t_order" primaryKey="id" joinKey="user_id" parentKey="id"/>
    </table>
</schema>

<dataNode name="dn1" dataHost="dh1" database="db0"/>
<dataNode name="dn2" dataHost="dh1" database="db1"/>

<dataHost name="dh1" maxCon="1000" minCon="10" balance="0"
          writeType="0" dbType="mysql" dbDriver="jdbc">
    <heartbeat>select user()</heartbeat>
    <writeHost host="host1" url="jdbc:mysql://localhost:3307/db0" 
               user="root" password="123456"/>
    <writeHost host="host2" url="jdbc:mysql://localhost:3307/db1" 
               user="root" password="123456"/>
</dataHost>
```

**rule.xml**（分片算法配置）：

```xml
<tableRule name="mod-long">
    <rule>
        <columns>user_id</columns>
        <algorithm>mod-long</algorithm>
    </rule>
</tableRule>

<function name="mod-long" class="io.mycat.route.function.PartitionByMod">
    <property name="count">2</property>
</function>
```

### 3.3 Java 应用配置

**application.yml**：

```yaml
mycat:
  enabled: true
  server-addr: localhost:8066
  username: root
  password: 123456
  database: test_db

spring:
  datasource:
    url: jdbc:mysql://localhost:8066/test_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: 123456
    driver-class-name: com.mysql.cj.jdbc.Driver
```

### 3.4 MyCat vs ShardingSphere

**选择建议**：
- **ShardingSphere**：适合中小型项目，分片逻辑简单，希望减少运维成本
- **MyCat**：适合大型项目，需要复杂路由规则，有专门的运维团队

---

## 四、流量路由配置

### 4.1 流量路由简介

**流量路由**用于根据规则将 SQL 请求路由到不同的数据源：
- **读写分离**：读请求路由到从库，写请求路由到主库
- **分库路由**：根据分片键路由到不同的数据库
- **表路由**：根据分片键路由到不同的表

### 4.2 TrafficRoutingConfig

**位置**：`common/src/main/java/com/lixiangyu/common/sharding/TrafficRoutingConfig.java`

**功能**：
- 管理路由规则
- 支持基于 SQL 类型和表名的路由
- 支持规则优先级
- 支持从 Nacos 动态加载

### 4.3 Nacos 路由配置

**Data ID**: `traffic.routing.config`

**配置格式**（JSON）：

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
      "targetDataSource": "ds-master",
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

**配置说明**：
- `rules`：路由规则列表
- `name`：规则名称
- `targetDataSource`：目标数据源
- `priority`：优先级（数字越小优先级越高）
- `sqlTypes`：匹配的 SQL 类型（SELECT/INSERT/UPDATE/DELETE）
- `tableNames`：匹配的表名列表
- `tablePatterns`：匹配的表名模式（支持通配符 * 和 ?）
- `defaultRule`：默认路由规则

### 4.4 使用示例

```java
@Autowired
private TrafficRoutingConfig trafficRoutingConfig;

public void executeQuery(String tableName) {
    // 获取目标数据源
    String targetDs = trafficRoutingConfig.getTargetDataSource("SELECT", tableName);
    
    // 使用目标数据源执行查询
    // ...
}
```

---

## 五、配置中心集成

### 5.1 Nacos 配置管理

所有分库分表配置都存储在 Nacos 配置中心：

| 配置项 | Data ID | 说明 |
|--------|---------|------|
| **数据源配置** | `sharding.config` | 数据源连接信息 |
| **分片规则** | `sharding.rules` | 分库分表规则和算法 |
| **路由规则** | `traffic.routing.config` | 流量路由规则 |

### 5.2 配置热更新

所有配置都支持热更新：
- 修改 Nacos 配置后，应用会自动重新加载
- 无需重启应用
- 配置变更会触发数据源重新初始化

### 5.3 配置优先级

1. **Nacos 配置中心**（最高优先级）
2. **application.yml**（默认配置）
3. **代码默认值**（最低优先级）

---

## 六、使用示例

### 6.1 启用 ShardingSphere

**步骤 1**：在 Nacos 中配置数据源

```json
{
  "dataSources": {
    "ds0": {
      "url": "jdbc:mysql://localhost:3307/db0?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai",
      "username": "root",
      "password": "123456",
      "driverClassName": "com.mysql.cj.jdbc.Driver"
    },
    "ds1": {
      "url": "jdbc:mysql://localhost:3307/db1?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai",
      "username": "root",
      "password": "123456",
      "driverClassName": "com.mysql.cj.jdbc.Driver"
    }
  }
}
```

**步骤 2**：在 Nacos 中配置分片规则

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
    }
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
    }
  }
}
```

**步骤 3**：启用 ShardingSphere

```yaml
sharding:
  enabled: true
```

### 6.2 使用 MyCat

**步骤 1**：部署 MyCat 中间件

**步骤 2**：配置 MyCat（schema.xml、rule.xml）

**步骤 3**：配置应用连接 MyCat

```yaml
mycat:
  enabled: true
  server-addr: localhost:8066

spring:
  datasource:
    url: jdbc:mysql://localhost:8066/test_db
    username: root
    password: 123456
```

---

## 七、最佳实践

### 7.1 分片键选择

**推荐**：
- ✅ 选择高基数列（如用户ID、订单ID）
- ✅ 选择业务查询常用的列
- ✅ 避免选择经常变更的列

**不推荐**：
- ❌ 选择低基数列（如性别、状态）
- ❌ 选择 NULL 值较多的列
- ❌ 选择经常变更的列

### 7.2 分片数量

**建议**：
- 分库数量：2-8 个（根据数据量和性能需求）
- 分表数量：单库 2-16 个表（避免过多表导致管理复杂）

### 7.3 配置管理

**建议**：
- 所有配置存储在 Nacos 配置中心
- 使用 JSON 格式存储配置
- 配置变更前先在测试环境验证
- 配置变更后监控应用日志

### 7.4 性能优化

**建议**：
- 合理设置连接池大小
- 启用 SQL 日志（调试时）
- 监控分片路由性能
- 避免跨分片查询

---

## 八、常见问题

### Q1: ShardingSphere 和 MyCat 可以同时使用吗？

**A**: 不建议同时使用，两者功能重叠。建议根据项目需求选择其一：
- **ShardingSphere**：适合中小型项目
- **MyCat**：适合大型项目，需要复杂路由规则

### Q2: 如何选择分片算法？

**A**: 
- **取模分片**：适合数据分布均匀的场景
- **范围分片**：适合按时间、ID 范围分片的场景
- **哈希分片**：适合需要均匀分布的场景
- **自定义分片**：适合复杂业务场景

### Q3: 如何处理跨分片查询？

**A**: 
- **避免跨分片查询**：尽量在 SQL 中包含分片键
- **使用 UNION**：ShardingSphere 会自动合并结果
- **使用聚合查询**：ShardingSphere 支持部分聚合函数

### Q4: 配置热更新会影响性能吗？

**A**: 
- 配置热更新会重新初始化数据源，会有短暂影响
- 建议在业务低峰期进行配置变更
- 配置变更后监控应用性能

---

## 九、总结

### 9.1 核心功能

✅ **ShardingSphere-JDBC 集成**：客户端分片，性能好，配置简单  
✅ **MyCat 集成**：服务端分片，适合复杂场景  
✅ **Nacos 配置中心集成**：统一管理配置，支持热更新  
✅ **流量路由**：支持读写分离、分库分表路由  
✅ **动态配置**：配置变更无需重启应用  

### 9.2 使用建议

1. **中小型项目**：使用 ShardingSphere-JDBC
2. **大型项目**：考虑使用 MyCat
3. **配置管理**：统一使用 Nacos 配置中心
4. **监控告警**：配置变更后监控应用状态

---

**文档版本**: 1.0  
**最后更新**: 2025-12-29

