# Canal 监听与 JPA 集成说明

## 文档信息

- **创建日期**: 2025-01-27
- **功能模块**: `com.lixiangyu.common.migration`
- **版本**: 2.1

---

## 一、功能概述

### 1.1 新增功能

1. **Canal 监听**：扩展 binlog 监听能力，支持 Alibaba Canal
2. **配置中心双写控制**：基于 Nacos 配置中心动态控制双写功能
3. **JPA 集成**：支持使用 JPA 操作数据库

### 1.2 功能对比

| 功能 | BinlogListener | CanalListener | 说明 |
|------|---------------|---------------|------|
| **稳定性** | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | Canal 更稳定 |
| **集群支持** | ❌ | ✅ | Canal 支持集群 |
| **性能** | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | Canal 性能更好 |
| **易用性** | ⭐⭐⭐ | ⭐⭐⭐⭐ | Canal 更易用 |

---

## 二、Canal 监听

### 2.1 功能说明

Canal 是 Alibaba 开源的基于数据库增量日志解析的组件，提供增量数据订阅和消费功能。

**优势**：
- ✅ 更稳定可靠
- ✅ 支持集群模式
- ✅ 更好的性能
- ✅ 支持多种数据源
- ✅ 支持表过滤表达式

### 2.2 使用方式

#### 方式一：编程式使用

```java
@Autowired
private CanalListener canalListener;

public void startCanalSync() {
    CanalListener.CanalListenerConfig config = CanalListener.CanalListenerConfig.builder()
            .sourceDataSource(sourceDataSource)
            .targetDataSource(targetDataSource)
            .canalServerAddress("localhost:11111")  // Canal 服务器地址
            .destination("example")                 // Canal 实例名称
            .username("canal")                      // Canal 用户名
            .password("canal")                      // Canal 密码
            .tables(Arrays.asList("user", "order")) // 要监听的表
            .subscribeFilter("test\\.t_.*")         // 订阅过滤表达式
            .batchSize(1000)                        // 批次大小
            .build();
    
    String taskId = canalListener.startListening(config);
    log.info("Canal 监听已启动，Task ID: {}", taskId);
}
```

#### 方式二：RESTful API

```bash
# 启动 Canal 监听
curl -X POST http://localhost:8080/api/migration/canal/start \
  -H "Content-Type: application/json" \
  -d '{
    "sourceDataSource": "sourceDataSource",
    "targetDataSource": "targetDataSource",
    "canalServerAddress": "localhost:11111",
    "destination": "example",
    "username": "canal",
    "password": "canal",
    "tables": ["user", "order"],
    "subscribeFilter": "test\\.t_.*",
    "batchSize": 1000
  }'

# 查询监听状态
curl http://localhost:8080/api/migration/canal/status/{taskId}

# 停止监听
curl -X POST http://localhost:8080/api/migration/canal/stop/{taskId}
```

### 2.3 Canal 配置

**Canal 服务器配置**（`canal.properties`）：

```properties
# Canal 服务器配置
canal.instance.master.address=127.0.0.1:3306
canal.instance.master.journal.name=
canal.instance.master.position=
canal.instance.master.timestamp=
canal.instance.master.gtid=

# 数据库配置
canal.instance.dbUsername=root
canal.instance.dbPassword=123456
canal.instance.connectionCharset=UTF-8
canal.instance.enableDruid=false

# 表过滤
canal.instance.filter.regex=.*\\..*
```

### 2.4 订阅过滤表达式

Canal 支持正则表达式过滤表：

```
# 监听所有表
.*\\..*

# 监听 test 库下所有表
test\\..*

# 监听 test 库下 t_ 开头的表
test\\.t_.*

# 监听多个表
test\\.user|test\\.order
```

---

## 三、配置中心双写控制

### 3.1 功能说明

基于 Nacos 配置中心动态控制双写功能，支持：
- 全局双写开关
- 写源库/写目标库独立控制
- 表级别的双写控制
- 配置变更自动生效

### 3.2 配置格式

在 Nacos 配置中心添加以下配置（`demo-dev.json`）：

```json
{
  "dual.write.enabled": "true",
  "dual.write.writeSource.enabled": "true",
  "dual.write.writeTarget.enabled": "true",
  "dual.write.tables": "user:true:true,order:true:true,product:false:true"
}
```

**配置说明**：
- `dual.write.enabled` - 全局双写开关
- `dual.write.writeSource.enabled` - 全局写源库开关
- `dual.write.writeTarget.enabled` - 全局写目标库开关
- `dual.write.tables` - 表级别配置（格式：表名:写源库:写目标库）

### 3.3 使用方式

```java
@Service
public class UserService {
    
    @DualWrite(
        source = "sourceDataSource",
        target = "targetDataSource",
        tables = {"user"}
    )
    public void saveUser(UserDO user) {
        // 双写功能会根据配置中心动态开启/关闭
        // 如果配置中心关闭了双写，则只写源库
        userMapper.insert(user);
    }
}
```

### 3.4 配置优先级

1. **表级别配置** > 全局配置
2. **写源库/写目标库** 独立控制
3. **配置变更** 自动生效（无需重启）

### 3.5 配置示例

**场景 1：全局开启双写**
```json
{
  "dual.write.enabled": "true",
  "dual.write.writeSource.enabled": "true",
  "dual.write.writeTarget.enabled": "true"
}
```

**场景 2：只写目标库（迁移完成，切换阶段）**
```json
{
  "dual.write.enabled": "true",
  "dual.write.writeSource.enabled": "false",
  "dual.write.writeTarget.enabled": "true"
}
```

**场景 3：表级别控制**
```json
{
  "dual.write.enabled": "false",
  "dual.write.tables": "user:true:true,order:false:true"
}
```
- `user` 表：写源库和目标库
- `order` 表：只写目标库
- 其他表：不双写

---

## 四、JPA 集成

### 4.1 功能说明

支持使用 JPA EntityManager 操作数据库，适用于使用 JPA 框架的项目。

### 4.2 使用方式

#### 方式一：配置操作器类型

```java
MigrationConfig config = MigrationConfig.builder()
        .source(MigrationConfig.DataSourceConfig.builder()
                .dataSource(sourceDataSource)
                .operatorType(MigrationConfig.DataSourceConfig.OperatorType.JPA)  // 使用 JPA规范.md
                .build())
        .target(MigrationConfig.DataSourceConfig.builder()
                .dataSource(targetDataSource)
                .operatorType(MigrationConfig.DataSourceConfig.OperatorType.JPA)
                .build())
        .build();
```

#### 方式二：双写中使用 JPA

```java
@Service
public class UserService {
    
    @Autowired
    private EntityManager entityManager;
    
    @DualWrite(
        source = "sourceDataSource",
        target = "targetDataSource",
        tables = {"user"}
    )
    public void saveUser(UserDO user) {
        // 使用 JPA规范.md 保存
        entityManager.persist(user);
        // 双写切面会自动识别 JPA规范.md 操作并同步到目标库
    }
}
```

### 4.3 JPA 特性支持

- ✅ 支持 JPA 实体类操作（persist、merge、remove）
- ✅ 支持原生 SQL 查询
- ✅ 自动事务管理
- ✅ 支持实体类注解（@Table、@Column、@Id）

### 4.4 JPA Repository 支持

双写切面会自动识别 JPA Repository 方法：

```java
@Repository
public interface UserRepository extends JpaRepository<UserDO, Long> {
    // 方法会自动被双写切面拦截
    UserDO save(UserDO user);
    void delete(UserDO user);
}
```

---

## 五、完整示例

### 5.1 Canal + 双写 + JPA

```java
@Service
@RequiredArgsConstructor
public class DatabaseMigrationService {
    
    private final DataMigrationService migrationService;
    private final CanalListener canalListener;
    private final EntityManager entityManager;
    
    /**
     * 完整的迁移流程：全量迁移 + Canal 增量同步 + 双写
     */
    public void fullMigrationWithCanalAndDualWrite() {
        // 1. 执行全量迁移
        MigrationConfig fullConfig = MigrationConfig.builder()
                .source(MigrationConfig.DataSourceConfig.builder()
                        .dataSource(sourceDataSource)
                        .operatorType(MigrationConfig.DataSourceConfig.OperatorType.JPA)
                        .build())
                .target(MigrationConfig.DataSourceConfig.builder()
                        .dataSource(targetDataSource)
                        .operatorType(MigrationConfig.DataSourceConfig.OperatorType.JPA)
                        .build())
                .tables(Arrays.asList("user", "order"))
                .build();
        
        MigrationResult result = migrationService.migrate(fullConfig);
        
        if (result.getStatus() == MigrationResult.MigrationStatus.SUCCESS) {
            // 2. 启动 Canal 增量同步
            CanalListener.CanalListenerConfig canalConfig = CanalListener.CanalListenerConfig.builder()
                    .sourceDataSource(sourceDataSource)
                    .targetDataSource(targetDataSource)
                    .canalServerAddress("localhost:11111")
                    .destination("example")
                    .tables(Arrays.asList("user", "order"))
                    .build();
            
            String canalTaskId = canalListener.startListening(canalConfig);
            log.info("Canal 增量同步已启动，Task ID: {}", canalTaskId);
        }
    }
    
    /**
     * 使用双写进行平滑切换（JPA规范.md）
     */
    @DualWrite(
        source = "sourceDataSource",
        target = "targetDataSource",
        tables = {"user", "order"}
    )
    public void saveUser(UserDO user) {
        // 使用 JPA规范.md 保存，自动双写
        entityManager.persist(user);
    }
}
```

### 5.2 配置中心控制双写

**Nacos 配置**（`demo-dev.json`）：

```json
{
  "dual.write.enabled": "true",
  "dual.write.writeSource.enabled": "true",
  "dual.write.writeTarget.enabled": "true",
  "dual.write.tables": "user:true:true,order:true:true"
}
```

**代码**：

```java
@DualWrite(
    source = "sourceDataSource",
    target = "targetDataSource",
    tables = {"user", "order"}
)
public void saveUser(UserDO user) {
    // 双写功能由配置中心控制
    // 可以通过修改 Nacos 配置动态开启/关闭
    userRepository.save(user);
}
```

---

## 六、依赖配置

### 6.1 必需依赖

已添加到 `common/pom.xml`：
- `spring-jdbc` - JDBC 支持
- `spring-context-support` - 任务调度支持

### 6.2 可选依赖

**Canal 客户端**（需要时取消注释）：
```xml
<dependency>
    <groupId>com.alibaba.otter</groupId>
    <artifactId>canal.client</artifactId>
    <version>1.1.7</version>
</dependency>
```

**JPA 支持**（需要时取消注释）：
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
```

---

## 七、配置中心配置说明

### 7.1 配置项

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `dual.write.enabled` | Boolean | false | 全局双写开关 |
| `dual.write.writeSource.enabled` | Boolean | true | 全局写源库开关 |
| `dual.write.writeTarget.enabled` | Boolean | false | 全局写目标库开关 |
| `dual.write.tables` | String | "" | 表级别配置 |

### 7.2 表级别配置格式

```
表名1:写源库:写目标库,表名2:写源库:写目标库
```

**示例**：
```
user:true:true,order:true:false,product:false:true
```

- `user` 表：写源库和目标库
- `order` 表：只写源库
- `product` 表：只写目标库

### 7.3 配置变更

配置变更会自动生效，无需重启应用：

1. 在 Nacos 控制台修改配置
2. 配置自动推送到应用
3. `DualWriteConfigManager` 监听配置变更
4. 双写行为立即生效

---

## 八、最佳实践

### 8.1 迁移流程

1. **准备阶段**
   - 配置 Canal 服务器
   - 在 Nacos 配置双写开关（关闭）

2. **全量迁移**
   - 执行全量迁移
   - 使用 JPA 操作器

3. **启动增量同步**
   - 启动 Canal 监听
   - 实时同步增量数据

4. **开启双写**
   - 在 Nacos 开启双写开关
   - 同时写入源库和目标库

5. **切换阶段**
   - 逐步关闭写源库
   - 只写目标库

6. **完成阶段**
   - 关闭双写
   - 停止 Canal 监听

### 8.2 配置建议

**迁移初期**：
```json
{
  "dual.write.enabled": "false"
}
```

**双写阶段**：
```json
{
  "dual.write.enabled": "true",
  "dual.write.writeSource.enabled": "true",
  "dual.write.writeTarget.enabled": "true"
}
```

**切换阶段**：
```json
{
  "dual.write.enabled": "true",
  "dual.write.writeSource.enabled": "false",
  "dual.write.writeTarget.enabled": "true"
}
```

---

## 九、API 参考

### 9.1 Canal 监听

- `POST /api/migration/canal/start` - 启动监听
- `POST /api/migration/canal/stop/{taskId}` - 停止监听
- `GET /api/migration/canal/status/{taskId}` - 查询状态

### 9.2 双写配置

- 通过 Nacos 配置中心动态控制
- 支持全局和表级别配置
- 配置变更自动生效

---

## 十、总结

### 10.1 实现成果

✅ **Canal 监听**：完整的 Canal 增量同步支持  
✅ **配置中心双写控制**：基于 Nacos 的动态双写控制  
✅ **JPA 集成**：完整的 JPA 数据库操作支持  

### 10.2 技术亮点

1. **Canal 集成**：使用反射避免直接依赖，支持降级
2. **动态配置**：基于配置中心的实时双写控制
3. **JPA 支持**：自动识别 JPA Repository 和实体类
4. **灵活控制**：支持全局和表级别的独立控制

### 10.3 适用场景

- ✅ 使用 Canal 进行增量同步
- ✅ 需要动态控制双写功能
- ✅ 使用 JPA 框架的项目
- ✅ 平滑切换数据库

---

**所有功能已完整实现！** 🎉

