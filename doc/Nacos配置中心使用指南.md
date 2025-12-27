# Nacos 配置中心使用指南

## 文档信息

- **创建日期**: 2025-12-26
- **问题类型**: 配置中心、配置热更新、配置监听
- **技术栈**: Spring Cloud 2021.0.10、Nacos 2021.0.5.0
- **影响范围**: web 模块、common 模块

---

## 概述

Nacos 是阿里巴巴开源的一个更易于构建云原生应用的动态服务发现、配置管理和服务管理平台。本项目使用 Nacos 作为配置中心，实现配置的集中管理和热更新。

### 核心功能

1. **配置集中管理**：所有配置统一存储在 Nacos 配置中心
2. **配置热更新**：修改配置后无需重启应用，自动生效
3. **配置监听**：可以监听配置变更，执行相应的业务逻辑
4. **多环境支持**：通过命名空间和 Data ID 支持多环境配置

---

## 环境准备

### 1. 安装 Nacos Server

#### 方式一：Docker 安装（推荐）

```bash
# 拉取 Nacos 镜像
docker pull nacos/nacos-server:v2.2.3

# 运行 Nacos Server
docker run -d \
  --name nacos-server \
  -p 8848:8848 \
  -p 9848:9848 \
  -e MODE=standalone \
  -e SPRING_DATASOURCE_PLATFORM=mysql \
  -e MYSQL_SERVICE_HOST=localhost \
  -e MYSQL_SERVICE_PORT=3306 \
  -e MYSQL_SERVICE_DB_NAME=nacos_config \
  -e MYSQL_SERVICE_USER=root \
  -e MYSQL_SERVICE_PASSWORD=root \
  nacos/nacos-server:v2.2.3
```

#### 方式二：本地安装

1. 下载 Nacos：https://github.com/alibaba/nacos/releases
2. 解压并进入 `bin` 目录
3. 启动（Windows）：
   ```bash
   startup.cmd -m standalone
   ```
4. 启动（Linux/Mac）：
   ```bash
   sh startup.sh -m standalone
   ```

### 2. 访问 Nacos 控制台

- **地址**：http://localhost:8848/nacos
- **默认用户名**：nacos
- **默认密码**：nacos

---

## 项目配置

### 1. 依赖配置

#### 父 POM 配置

在 `pom.xml` 中添加 Nacos 版本管理：

```xml
<properties>
    <nacos.version>2021.0.5.0</nacos.version>
</properties>

<dependencyManagement>
    <dependencies>
        <!-- Nacos 配置中心依赖版本管理 -->
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
            <version>${nacos.version}</version>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
            <version>${nacos.version}</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

#### Web 模块依赖

在 `web/pom.xml` 中添加依赖：

```xml
<dependencies>
    <!-- Nacos 配置中心 -->
    <dependency>
        <groupId>com.alibaba.cloud</groupId>
        <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
    </dependency>
    <dependency>
        <groupId>com.alibaba.cloud</groupId>
        <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
    </dependency>
</dependencies>
```

### 2. 配置文件

#### bootstrap.yml（必须）

创建 `web/src/main/resources/bootstrap.yml`：

```yaml
spring:
  application:
    name: demo
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  cloud:
    nacos:
      # 配置中心配置
      config:
        # Nacos 服务器地址
        server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:8848}
        # 配置文件的命名空间（可选，用于环境隔离）
        namespace: ${NACOS_NAMESPACE:}
        # 配置文件的 Data ID（默认使用 spring.application.name）
        # 格式：${spring.application.name}-${spring.profiles.active}.${file-extension}
        # 例如：demo-dev.yml
        file-extension: yml
        # 配置分组（可选，默认为 DEFAULT_GROUP）
        group: ${NACOS_CONFIG_GROUP:DEFAULT_GROUP}
        # 是否启用配置中心（默认 true）
        enabled: ${NACOS_CONFIG_ENABLED:true}
        # 配置刷新（热更新）开关
        refresh-enabled: true
      # 服务发现配置（可选）
      discovery:
        server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:8848}
        namespace: ${NACOS_NAMESPACE:}
        enabled: ${NACOS_DISCOVERY_ENABLED:false}
```

**关键说明**：
- `bootstrap.yml` 的优先级高于 `application.yml`
- Nacos 配置必须在 `bootstrap.yml` 中配置
- `refresh-enabled: true` 启用配置热更新

---

## Nacos 配置中心使用

### 1. 在 Nacos 控制台创建配置

#### 步骤 1：登录 Nacos 控制台

访问 http://localhost:8848/nacos，使用默认账号密码登录。

#### 步骤 2：创建配置

1. 进入 **配置管理** → **配置列表**
2. 点击 **+** 按钮创建配置
3. 填写配置信息：
   - **Data ID**：`demo-dev.yml`（格式：`${spring.application.name}-${spring.profiles.active}.yml`）
   - **Group**：`DEFAULT_GROUP`（默认分组）
   - **配置格式**：`YAML`
   - **配置内容**：填写 YAML 格式的配置

#### 步骤 3：配置内容示例

```yaml
# 业务配置
business:
  batch:
    size: 1000
    timeout: 30000

# 线程池配置
thread:
  pool:
    core:
      size: 10
    max:
      size: 20
    queue:
      capacity: 200

# 功能开关
feature:
  enabled: true
  cache:
    enabled: true

# 数据库连接池配置（示例，通常不建议在配置中心配置）
spring:
  datasource:
    hikari:
      minimum-idle: 5
      maximum-pool-size: 20
```

#### 步骤 4：发布配置

点击 **发布** 按钮，配置立即生效（如果 `refresh-enabled: true`）。

### 2. 配置命名规则

#### Data ID 命名规则

```
${spring.application.name}-${spring.profiles.active}.${file-extension}
```

**示例**：
- 应用名称：`demo`
- 激活环境：`dev`
- 文件扩展名：`yml`
- **Data ID**：`demo-dev.yml`

#### 多环境配置

| 环境 | Data ID | 说明 |
|------|---------|------|
| 开发环境 | `demo-dev.yml` | 开发环境配置 |
| 测试环境 | `demo-test.yml` | 测试环境配置 |
| 生产环境 | `demo-prod.yml` | 生产环境配置 |

#### 命名空间（Namespace）

用于环境隔离，不同命名空间的配置互不影响。

**使用场景**：
- 开发环境：`dev`
- 测试环境：`test`
- 生产环境：`prod`

**配置方式**：
```yaml
spring:
  cloud:
    nacos:
      config:
        namespace: ${NACOS_NAMESPACE:dev}  # 通过环境变量或启动参数指定
```

---

## 配置热更新

### 1. 原理说明

Nacos 配置中心支持配置热更新，修改配置后无需重启应用即可生效。

**工作流程**：
1. 应用启动时从 Nacos 拉取配置
2. Nacos Server 检测到配置变更
3. Nacos Client 通过长轮询或推送机制获取变更通知
4. Spring Cloud 刷新配置并发布 `EnvironmentChangeEvent` 事件
5. 应用中的监听器接收事件并处理

### 2. 使用 @RefreshScope 注解

对于需要热更新的 Bean，使用 `@RefreshScope` 注解：

```java
import org.springframework.cloud.context.config.annotation.RefreshScope;

@RefreshScope
@Component
public class BusinessConfig {
    
    @Value("${business.batch.size:1000}")
    private Integer batchSize;
    
    @Value("${thread.pool.core.size:10}")
    private Integer corePoolSize;
    
    // getter/setter...
}
```

**说明**：
- `@RefreshScope` 使 Bean 在配置更新时重新创建
- 使用 `@Value` 注解注入配置值
- 配置更新后，Bean 会重新创建，新的配置值会生效

### 3. 使用 @ConfigurationProperties 注解

更推荐使用 `@ConfigurationProperties` 进行配置绑定：

```java
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

@RefreshScope
@Component
@ConfigurationProperties(prefix = "business")
public class BusinessProperties {
    
    private Batch batch = new Batch();
    private Feature feature = new Feature();
    
    // getter/setter...
    
    @Data
    public static class Batch {
        private Integer size = 1000;
        private Integer timeout = 30000;
    }
    
    @Data
    public static class Feature {
        private Boolean enabled = true;
        private Cache cache = new Cache();
        
        @Data
        public static class Cache {
            private Boolean enabled = true;
        }
    }
}
```

**优势**：
- 类型安全
- 支持嵌套配置
- IDE 自动补全
- 配置验证

---

## 配置监听

### 1. 使用 DynamicConfigManager

项目提供了 `DynamicConfigManager` 工具类，支持配置读取和监听：

```java
import com.lixiangyu.common.config.DynamicConfigManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MyService {
    
    @Autowired
    private DynamicConfigManager configManager;
    
    public void init() {
        // 读取配置
        Integer batchSize = configManager.getInteger("business.batch.size", 1000);
        
        // 注册配置变更监听器
        configManager.addListener("business.batch.size", (key, newValue) -> {
            log.info("批次大小配置已更新：{} = {}", key, newValue);
            // 更新业务逻辑
            updateBatchSize(Integer.parseInt(newValue));
        });
    }
}
```

### 2. 监听 EnvironmentChangeEvent

实现 `ApplicationListener<EnvironmentChangeEvent>` 接口：

```java
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public class ConfigChangeListener implements ApplicationListener<EnvironmentChangeEvent> {
    
    @Override
    public void onApplicationEvent(EnvironmentChangeEvent event) {
        Set<String> changedKeys = event.getKeys();
        log.info("配置变更：{}", changedKeys);
        
        for (String key : changedKeys) {
            // 处理配置变更
            handleConfigChange(key);
        }
    }
}
```

### 3. 使用 NacosConfigListener

项目已提供 `NacosConfigListener`，可以在此基础上扩展：

```java
@Component
public class CustomConfigListener extends NacosConfigListener {
    
    @Override
    protected void handleConfigChange(String key) {
        super.handleConfigChange(key);
        
        // 自定义处理逻辑
        if (key.equals("business.batch.size")) {
            // 重新初始化相关组件
        }
    }
}
```

---

## 配置优先级

### 优先级顺序（从高到低）

1. **Nacos 配置中心配置**（最高优先级）
2. **bootstrap.yml** 配置
3. **application.yml** 配置
4. **application-{profile}.yml** 配置
5. **默认值**（代码中的默认值）

### 配置覆盖规则

- Nacos 配置中心的配置会覆盖本地配置文件
- 相同 key 的配置，后加载的会覆盖先加载的
- 建议：基础配置放在本地，动态配置放在 Nacos

---

## 最佳实践

### 1. 配置分类

#### 放在 Nacos 的配置

- ✅ **业务配置**：业务规则、开关、阈值等
- ✅ **第三方服务配置**：外部 API 地址、密钥等
- ✅ **动态配置**：需要频繁调整的配置
- ✅ **环境相关配置**：不同环境不同的配置

#### 放在本地文件的配置

- ✅ **基础配置**：应用名称、端口等
- ✅ **数据库配置**：数据源配置（通常不建议热更新）
- ✅ **框架配置**：Spring、MyBatis 等框架配置
- ✅ **固定配置**：不会频繁变更的配置

### 2. 配置命名规范

```yaml
# 业务配置：使用 business 前缀
business:
  batch:
    size: 1000
  feature:
    enabled: true

# 线程池配置：使用 thread.pool 前缀
thread:
  pool:
    core:
      size: 10

# 第三方服务配置：使用 service 前缀
service:
  api:
    url: https://api.example.com
    timeout: 5000
```

### 3. 配置热更新注意事项

#### ✅ 支持热更新的配置

- 业务参数（批次大小、超时时间等）
- 功能开关
- 第三方服务地址
- 缓存配置

#### ❌ 不支持热更新的配置

- 数据库连接配置（需要重启才能生效）
- 线程池核心配置（需要重启才能生效）
- Bean 的创建方式（需要重启才能生效）

### 4. 配置监听最佳实践

```java
@Component
public class ConfigChangeHandler {
    
    @Autowired
    private DynamicConfigManager configManager;
    
    @PostConstruct
    public void init() {
        // 1. 监听业务配置
        configManager.addListener("business.batch.size", (key, newValue) -> {
            Integer newSize = Integer.parseInt(newValue);
            log.info("批次大小已更新为：{}", newSize);
            // 更新业务逻辑
        });
        
        // 2. 监听功能开关
        configManager.addListener("feature.enabled", (key, newValue) -> {
            Boolean enabled = Boolean.parseBoolean(newValue);
            if (enabled) {
                enableFeature();
            } else {
                disableFeature();
            }
        });
        
        // 3. 监听多个相关配置
        String[] relatedKeys = {"business.batch.size", "business.batch.timeout"};
        for (String key : relatedKeys) {
            configManager.addListener(key, (k, v) -> {
                reloadBusinessConfig();
            });
        }
    }
}
```

---

## 使用示例

### 示例 1：读取配置

```java
@Service
public class UpdateServiceImpl implements UpdateService {
    
    @Autowired
    private DynamicConfigManager configManager;
    
    public UpdateResult batchUpdate(Integer count) {
        // 从配置中心读取批次大小
        Integer batchSize = configManager.getInteger("business.batch.size", 1000);
        Integer timeout = configManager.getInteger("business.batch.timeout", 30000);
        
        // 使用配置值
        // ...
    }
}
```

### 示例 2：监听配置变更

```java
@Component
public class ThreadPoolConfigUpdater {
    
    @Autowired
    private DynamicConfigManager configManager;
    
    @Autowired
    private ThreadPoolTaskExecutor executor;
    
    @PostConstruct
    public void init() {
        // 监听线程池配置变更
        configManager.addListener("thread.pool.core.size", (key, newValue) -> {
            Integer newSize = Integer.parseInt(newValue);
            log.warn("线程池核心线程数配置已更新为：{}，但需要重启应用才能生效", newSize);
            // 注意：线程池配置通常需要重启才能生效
        });
    }
}
```

### 示例 3：使用 @RefreshScope

```java
@RefreshScope
@Component
@ConfigurationProperties(prefix = "business")
public class BusinessConfig {
    
    private Batch batch = new Batch();
    
    @Data
    public static class Batch {
        private Integer size = 1000;
        private Integer timeout = 30000;
    }
}

// 使用
@Service
public class MyService {
    
    @Autowired
    private BusinessConfig businessConfig;
    
    public void doSomething() {
        Integer batchSize = businessConfig.getBatch().getSize();
        // 配置更新后，businessConfig 会重新创建，新值自动生效
    }
}
```

---

## 常见问题

### Q1: 配置更新后不生效？

**A**: 检查以下几点：
1. 确认 `refresh-enabled: true` 已配置
2. 确认使用了 `@RefreshScope` 注解（如果使用 `@Value` 或 `@ConfigurationProperties`）
3. 确认 Data ID 和 Group 配置正确
4. 查看日志，确认配置已从 Nacos 拉取

### Q2: 如何查看当前生效的配置？

**A**: 使用 Spring Boot Actuator：

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: env,configprops
```

访问：http://localhost:8080/actuator/env

### Q3: 配置更新后如何验证？

**A**: 
1. 在 Nacos 控制台修改配置
2. 查看应用日志，确认收到配置变更事件
3. 调用相关接口，验证配置是否生效

### Q4: 如何实现配置的灰度发布？

**A**: 使用 Nacos 的配置分组（Group）功能：
- 创建多个 Group（如：`group-v1`、`group-v2`）
- 不同服务实例使用不同的 Group
- 逐步切换 Group 实现灰度发布

### Q5: 配置中心连接失败怎么办？

**A**: 
1. 检查 Nacos Server 是否启动
2. 检查网络连接
3. 检查 `server-addr` 配置是否正确
4. 查看应用启动日志，确认连接状态

---

## 配置中心架构图

```
┌─────────────────┐
│  Nacos Server   │
│  (配置中心)      │
└────────┬────────┘
         │
         │ 拉取配置 / 监听变更
         │
┌────────▼────────┐
│  Spring Boot    │
│   Application   │
│                 │
│  ┌───────────┐  │
│  │ bootstrap │  │
│  │   .yml    │  │
│  └───────────┘  │
│                 │
│  ┌───────────┐  │
│  │  Config   │  │
│  │ Listener │  │
│  └───────────┘  │
└─────────────────┘
```

---

## 总结

### 核心优势

1. **配置集中管理**：所有配置统一在 Nacos 管理
2. **配置热更新**：修改配置无需重启应用
3. **配置监听**：可以监听配置变更并执行相应逻辑
4. **多环境支持**：通过命名空间和 Data ID 支持多环境

### 推荐使用方式

1. **业务配置**：使用 `DynamicConfigManager` 读取和监听
2. **结构化配置**：使用 `@ConfigurationProperties` + `@RefreshScope`
3. **简单配置**：使用 `@Value` + `@RefreshScope`

---

## 更新记录

| 日期 | 版本 | 更新内容 | 作者 |
|------|------|---------|------|
| 2025-12-26 | 1.0 | 初始版本，实现 Nacos 配置中心集成 | lixiangyu |

---

**文档版本**: 1.0  
**最后更新**: 2025-12-26  
**适用项目**: com.lixiangyu.demo

