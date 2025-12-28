# Nacos 连接失败问题解决方案

## 文档信息

- **创建日期**: 2025-01-27
- **问题类型**: Nacos 连接失败 - `endpoint is blank`
- **错误信息**: `NacosConnectionFailureException: endpoint is blank`
- **影响版本**: Spring Boot 2.4+、Spring Cloud 2021.x

---

## 一、问题描述

### 1.1 错误信息

```
com.alibaba.cloud.nacos.diagnostics.analyzer.NacosConnectionFailureException: 
java.lang.reflect.InvocationTargetException

Caused by: com.alibaba.nacos.api.exception.NacosException: endpoint is blank

Description:
Application failed to connect to Nacos server: "null"

Action:
Please check your Nacos server config
```

### 1.2 问题原因

**根本原因**：Nacos 服务器地址配置在 `bootstrap.yml` 中，但 Spring Boot 2.4+ 不再自动加载 `bootstrap.yml` 文件。

**技术背景**：
1. **Spring Boot 2.4+ 变更**：从 Spring Boot 2.4 开始，`bootstrap.yml` 不再自动加载
2. **配置加载顺序**：使用 `spring.config.import` 导入 Nacos 配置时，需要先知道 Nacos 服务器地址
3. **配置缺失**：如果 Nacos 配置在 `bootstrap.yml` 中，应用启动时无法读取到 `server-addr`，导致 `endpoint is blank`

**问题流程**：
```
1. 应用启动
2. 读取 spring.config.import=nacos:demo-dev.yml
3. 尝试连接 Nacos 服务器
4. 查找 spring.cloud.nacos.config.server-addr
5. ❌ 配置在 bootstrap.yml 中，但 bootstrap.yml 未被加载
6. server-addr 为空 → endpoint is blank
7. 连接失败
```

---

## 二、解决方案

### 方案一：将 Nacos 配置移到 application.yml（推荐）

**适用场景**：需要使用 Nacos 配置中心

**步骤**：

1. **将 Nacos 配置从 `bootstrap.yml` 移到 `application.yml`**：

```yaml
# application.yml
spring:
  application:
    name: demo
  profiles:
    active: dev
  
  # Nacos 配置导入
  config:
    import: nacos:${spring.application.name}-${spring.profiles.active}.yml
  
  # Nacos 配置中心配置（必须放在 application.yml 中）
  cloud:
    nacos:
      config:
        server-addr: localhost:8848
        namespace: 
        group: DEFAULT_GROUP
        file-extension: yml
        enabled: true
        refresh-enabled: true
      discovery:
        server-addr: localhost:8848
        enabled: false
```

2. **保留或删除 `bootstrap.yml`**：

- **选项A**：删除 `bootstrap.yml`（推荐）
- **选项B**：保留 `bootstrap.yml` 但不再使用（仅作参考）

**完整配置示例**：

```yaml
# application.yml
server:
  port: 8080

spring:
  application:
    name: demo
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  
  # ==================== Nacos 配置导入 ====================
  config:
    import: nacos:${spring.application.name}-${spring.profiles.active}.yml
  
  # ==================== Nacos 配置中心配置 ====================
  cloud:
    nacos:
      config:
        server-addr: ${NACOS_SERVER_ADDR:localhost:8848}
        namespace: ${NACOS_NAMESPACE:}
        file-extension: yml
        group: ${NACOS_CONFIG_GROUP:DEFAULT_GROUP}
        enabled: true
        refresh-enabled: true
        import-check:
          enabled: true
      discovery:
        server-addr: ${NACOS_SERVER_ADDR:localhost:8848}
        namespace: ${NACOS_NAMESPACE:}
        enabled: false
```

---

### 方案二：使用可选导入（Nacos 可选）

**适用场景**：Nacos 配置不是必需的，如果 Nacos 不可用也不影响应用启动

**配置方式**：

```yaml
spring:
  config:
    # 使用 optional: 前缀，允许 Nacos 不可用时应用仍可启动
    import: optional:nacos:${spring.application.name}-${spring.profiles.active}.yml
  cloud:
    nacos:
      config:
        server-addr: localhost:8848
        # 即使 Nacos 不可用，应用也能启动
```

**说明**：
- `optional:` 前缀表示如果 Nacos 不可用，应用仍可正常启动
- 适用于开发环境或 Nacos 非必需场景
- 如果 Nacos 可用，仍会正常加载配置

---

### 方案三：禁用 Nacos 配置（不使用 Nacos）

**适用场景**：不需要使用 Nacos 配置中心

**配置方式**：

```yaml
spring:
  # 注释掉 Nacos 配置导入
  # config:
  #   import: nacos:demo-dev.yml
  cloud:
    nacos:
      config:
        # 禁用导入检查
        import-check:
          enabled: false
        # 禁用配置中心
        enabled: false
```

**或者完全移除 Nacos 依赖**：

```xml
<!-- 从 pom.xml 中移除 -->
<!--
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
</dependency>
-->
```

---

## 三、配置加载顺序

### 3.1 Spring Boot 2.4+ 配置加载顺序

**新的加载顺序**（从高到低）：
1. **命令行参数**
2. **环境变量**
3. **application-{profile}.yml**
4. **application.yml**
5. **通过 `spring.config.import` 导入的配置**（如 Nacos）
6. ~~`bootstrap.yml`~~（不再自动加载）

### 3.2 关键点

1. **`bootstrap.yml` 不再自动加载**：
   - Spring Boot 2.4+ 移除了对 `bootstrap.yml` 的自动支持
   - 如果需要使用，必须通过 `spring.config.import` 显式导入

2. **Nacos 配置必须在 `application.yml` 中**：
   - `spring.config.import` 在 `application.yml` 中执行
   - Nacos 客户端需要先知道服务器地址才能连接
   - 因此 `server-addr` 必须在 `application.yml` 中配置

3. **配置导入时机**：
   ```
   应用启动
   → 读取 application.yml
   → 执行 spring.config.import
   → 需要 Nacos 配置（server-addr）
   → 如果配置在 bootstrap.yml → ❌ 找不到
   → 如果配置在 application.yml → ✅ 找到
   ```

---

## 四、完整迁移步骤

### 4.1 从 bootstrap.yml 迁移到 application.yml

**步骤 1**：查看 `bootstrap.yml` 中的 Nacos 配置

```yaml
# bootstrap.yml（旧配置）
spring:
  cloud:
    nacos:
      config:
        server-addr: localhost:8848
        file-extension: yml
```

**步骤 2**：将配置复制到 `application.yml`

```yaml
# application.yml（新配置）
spring:
  application:
    name: demo
  profiles:
    active: dev
  config:
    import: nacos:demo-dev.yml
  cloud:
    nacos:
      config:
        server-addr: localhost:8848
        file-extension: yml
```

**步骤 3**：删除或重命名 `bootstrap.yml`

- 删除 `bootstrap.yml`（推荐）
- 或重命名为 `bootstrap.yml.bak`（保留备份）

**步骤 4**：验证配置

1. 确保 Nacos 服务器已启动
2. 在 Nacos 控制台创建配置（Data ID: `demo-dev.yml`）
3. 重启应用，检查是否成功连接

---

## 五、常见问题

### 5.1 问题：迁移后仍然报错

**可能原因**：
1. Nacos 服务器未启动
2. Nacos 服务器地址配置错误
3. 配置文件格式错误

**解决方案**：

1. **检查 Nacos 是否启动**：
```bash
# 检查 Nacos 容器状态
docker ps | grep nacos

# 检查 Nacos 健康状态
curl http://localhost:8848/nacos/v1/console/health/liveness
```

2. **检查配置地址**：
```yaml
spring:
  cloud:
    nacos:
      config:
        server-addr: localhost:8848  # 确保地址正确
```

3. **检查配置格式**：
```yaml
# ✅ 正确
spring:
  cloud:
    nacos:
      config:
        server-addr: localhost:8848

# ❌ 错误（缺少 cloud）
spring:
  nacos:
    config:
      server-addr: localhost:8848
```

### 5.2 问题：配置在 Nacos 中但无法加载

**可能原因**：
1. Data ID 不匹配
2. Group 不匹配
3. Namespace 不匹配

**解决方案**：

1. **检查 Data ID**：
```yaml
spring:
  config:
    import: nacos:demo-dev.yml  # 必须与 Nacos 中的 Data ID 一致
```

2. **检查 Group**：
```yaml
spring:
  cloud:
    nacos:
      config:
        group: DEFAULT_GROUP  # 必须与 Nacos 中的 Group 一致
```

3. **检查 Namespace**：
```yaml
spring:
  cloud:
    nacos:
      config:
        namespace:  # 如果 Nacos 中使用了命名空间，这里也要配置
```

### 5.3 问题：应用启动慢

**可能原因**：
1. Nacos 服务器连接超时
2. 网络问题

**解决方案**：

1. **增加超时时间**：
```yaml
spring:
  cloud:
    nacos:
      config:
        server-addr: localhost:8848
        timeout: 10000  # 超时时间（毫秒）
```

2. **使用可选导入**：
```yaml
spring:
  config:
    import: optional:nacos:demo-dev.yml
```

---

## 六、最佳实践

### 6.1 配置建议

1. **统一使用 `application.yml`**：
   - Spring Boot 2.4+ 推荐将所有配置放在 `application.yml`
   - 避免使用 `bootstrap.yml`（除非有特殊需求）

2. **使用环境变量**：
```yaml
spring:
  cloud:
    nacos:
      config:
        server-addr: ${NACOS_SERVER_ADDR:localhost:8848}
```

3. **使用可选导入**（开发环境）：
```yaml
spring:
  config:
    import: optional:nacos:demo-dev.yml
```

### 6.2 配置结构

**推荐的配置结构**：

```yaml
# application.yml
spring:
  application:
    name: demo
  profiles:
    active: dev
  
  # Nacos 配置导入（必须在 cloud.nacos 配置之前）
  config:
    import: nacos:${spring.application.name}-${spring.profiles.active}.yml
  
  # Nacos 配置（必须在 application.yml 中）
  cloud:
    nacos:
      config:
        server-addr: localhost:8848
        file-extension: yml
        enabled: true
```

---

## 七、版本兼容性

| Spring Boot 版本 | Spring Cloud 版本 | bootstrap.yml | 配置位置 |
|-----------------|------------------|--------------|---------|
| 2.3.x | 2020.x | ✅ 自动加载 | bootstrap.yml 或 application.yml |
| 2.4+ | 2020.x | ⚠️ 需显式导入 | application.yml（推荐） |
| 2.4+ | 2021.x | ❌ 不再支持 | **application.yml（必需）** |
| 3.0+ | 2022.x | ❌ 不再支持 | application.yml（必需） |

**当前项目版本**：
- Spring Boot: 2.7.18
- Spring Cloud: 2021.0.9
- Spring Cloud Alibaba: 2021.0.5.0

**结论**：必须将 Nacos 配置放在 `application.yml` 中。

---

## 八、总结

### 8.1 核心要点

1. **Spring Boot 2.4+** 不再自动加载 `bootstrap.yml`
2. **Nacos 配置** 必须放在 `application.yml` 中
3. **配置顺序**：先配置 `server-addr`，再导入配置
4. **可选导入**：使用 `optional:` 前缀允许 Nacos 不可用

### 8.2 快速修复

**最小配置**（在 `application.yml` 中）：
```yaml
spring:
  application:
    name: demo
  profiles:
    active: dev
  config:
    import: nacos:demo-dev.yml
  cloud:
    nacos:
      config:
        server-addr: localhost:8848
```

### 8.3 验证步骤

1. ✅ 将 Nacos 配置从 `bootstrap.yml` 移到 `application.yml`
2. ✅ 确保 Nacos 服务器已启动
3. ✅ 在 Nacos 控制台创建配置（Data ID: `demo-dev.yml`）
4. ✅ 重启应用，检查是否成功连接

---

## 九、参考资源

- [Spring Boot 2.4 配置变更](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-2.4-Release-Notes#config-data-import)
- [Spring Cloud 2021.x 文档](https://spring.io/projects/spring-cloud)
- [Nacos 官方文档](https://nacos.io/docs/latest/what-is-nacos/)
- [Spring Cloud Alibaba 文档](https://github.com/alibaba/spring-cloud-alibaba)

