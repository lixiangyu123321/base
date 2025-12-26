# JDK、Spring Framework、Spring Boot 版本适配指南

## 文档信息

- **创建日期**: 2025-12-26
- **适用项目**: com.lixiangyu.demo
- **当前配置**: Spring Boot 2.7.18, Spring Framework 5.3.31, Java 8

---

## 版本适配总览

### 当前项目配置

| 组件 | 当前版本 | 推荐版本 | 说明 |
|------|---------|---------|------|
| **JDK** | 8 | 8 | 稳定版本，广泛使用 |
| **Spring Boot** | 2.7.18 | 2.7.18 | 2.x 系列最终版本，支持 JDK 8 |
| **Spring Framework** | 5.3.31 | 5.3.31 | 与 Spring Boot 2.7.18 配套 |

---

## 版本兼容性矩阵

### Spring Boot 与 JDK 版本兼容性

| Spring Boot 版本 | 最低 JDK 版本 | 推荐 JDK 版本 | 支持的最高 JDK 版本 | Spring Framework 版本 |
|----------------|-------------|-------------|-------------------|---------------------|
| **2.7.x** | 8 | 8, 11, 17 | 17 | 5.3.x |
| **3.0.x** | 17 | 17, 19 | 19 | 6.0.x |
| **3.1.x** | 17 | 17, 20 | 20 | 6.0.x |
| **3.2.x** | 17 | 17, 21 | 21 | 6.1.x |
| **3.3.x** | 17 | 17, 22 | 22 | 6.1.x |
| **3.4.x** | 17 | 17, 23 | 23 | 6.2.x |
| **3.5.x** | 17 | 17, 24 | 24 | 6.2.x |
| **4.0.x** | **17** | **17, 21, 25** | 25 | **7.0.x** |

### 关键说明

1. **Spring Boot 3.0+ 不再支持 Java 8**
   - 最低要求 Java 17
   - 原因：Spring Framework 6.0+ 需要 Java 17+

2. **Spring Boot 4.0.x 版本要求**
   - **最低 JDK**: 17
   - **推荐 JDK**: 17 (LTS) 或 21 (LTS)
   - **支持 JDK**: 17, 21, 25
   - **Spring Framework**: 7.0.x

3. **LTS 版本推荐**
   - **Java 17** (2021-09, LTS, 支持至 2029-09)
   - **Java 21** (2023-09, LTS, 支持至 2031-09)
   - **Java 25** (2025-09, 非 LTS)

---

## 当前项目版本选择

### 推荐配置：Java 8

**选择理由**：
1. ✅ **稳定版本**：广泛使用，生态成熟
2. ✅ **兼容性好**：与 Spring Boot 2.7.18 完美兼容
3. ✅ **团队友好**：学习成本低，无需学习新特性
4. ✅ **工具链完善**：IDE、构建工具支持完善
5. ✅ **生产验证**：大量生产环境验证，稳定可靠

### 备选配置：Java 21

**适用场景**：
- 需要最新语言特性（如虚拟线程、模式匹配等）
- 追求更高性能
- 团队技术栈较新

**注意事项**：
- 部分第三方库可能尚未完全支持 Java 21
- 需要验证所有依赖的兼容性

---

## JDK 版本特性对比

### Java 17 (推荐)

**发布时间**: 2021-09-14  
**LTS 支持**: 至 2029-09  
**主要特性**:
- Sealed Classes（密封类）
- Pattern Matching for switch（switch 模式匹配预览）
- Records（记录类）
- Text Blocks（文本块）
- 性能优化和 GC 改进

**适用场景**:
- ✅ 企业级应用开发
- ✅ 长期维护的项目
- ✅ 稳定性和兼容性要求高

### Java 21 (备选)

**发布时间**: 2023-09-19  
**LTS 支持**: 至 2031-09  
**主要特性**:
- Virtual Threads（虚拟线程）- Project Loom
- Pattern Matching for switch（正式版）
- Record Patterns（记录模式）
- Sequenced Collections（有序集合）
- 更多性能优化

**适用场景**:
- ✅ 高并发应用
- ✅ 需要最新语言特性
- ✅ 新项目开发

### Java 25 (不推荐用于生产)

**发布时间**: 2025-09  
**LTS 支持**: 否  
**说明**: 非 LTS 版本，不建议用于生产环境

---

## Spring Boot 版本选择建议

### Spring Boot 4.0.0 特性

**发布时间**: 2025年  
**主要特性**:
- 基于 Spring Framework 7.0
- 支持 Java 17, 21, 25
- 性能优化和内存改进
- 新的自动配置机制
- 更好的 GraalVM 原生镜像支持

**适用场景**:
- ✅ 新项目开发
- ✅ 需要最新 Spring 特性
- ✅ 追求性能和现代化

### 版本选择建议

| 项目类型 | 推荐 Spring Boot 版本 | 推荐 JDK 版本 |
|---------|---------------------|--------------|
| **新项目（Java 8）** | 2.7.18 | 8 |
| **新项目（Java 17+）** | 3.3.x 或 4.0.x | 17 或 21 |
| **现有项目升级** | 2.7.18 (Java 8) 或 3.3.x (Java 17) | 8 或 17 |
| **企业级项目** | 2.7.18 (稳定) | 8 |
| **实验性项目** | 4.0.x | 21 |

---

## 版本配置示例

### Maven 配置 (pom.xml)

```xml
<properties>
    <!-- Java 版本配置 -->
    <java.version>1.8</java.version>
    <maven.compiler.source>1.8</maven.compiler.source>
    <maven.compiler.target>1.8</maven.compiler.target>
    
    <!-- 编码配置 -->
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
    
    <!-- Spring Boot 版本 -->
    <spring-boot.version>2.7.18</spring-boot.version>
</properties>

<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>2.7.18</version>
</parent>
```

### Gradle 配置 (build.gradle)

```gradle
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

plugins {
    id 'org.springframework.boot' version '4.0.0'
    id 'io.spring.dependency-management' version '1.1.6'
}
```

---

## 版本升级路径

### 从 Java 8 升级到 Java 17

**步骤**:
1. 更新 `pom.xml` 中的 Java 版本配置
2. 检查并更新不兼容的依赖
3. 修复废弃 API 的使用
4. 测试所有功能模块

**常见问题**:
- `javax.*` 包改为 `jakarta.*` (Spring Boot 3.0+)
- 反射访问限制更严格
- 模块系统 (JPMS) 相关调整

### 从 Spring Boot 2.x 升级到 3.x/4.x

**步骤**:
1. 先升级到 Spring Boot 3.0.x
2. 解决 Java 8 → Java 17 的兼容性问题
3. 处理 `javax.*` → `jakarta.*` 迁移
4. 更新配置文件格式
5. 测试验证

**主要变更**:
- Java 最低版本：8 → 17
- Servlet API：javax → jakarta
- 配置属性变更
- 自动配置调整

### 当前项目版本调整记录（Java 8 + Spring Boot 2.7.18）

**调整日期**: 2025-12-26

**调整内容**:

1. **Java 版本调整**
   - 从：Java 17
   - 到：Java 8
   - 配置位置：`pom.xml` 中的 `<java.version>1.8</java.version>`

2. **Spring Boot 版本调整**
   - 从：Spring Boot 4.0.0
   - 到：Spring Boot 2.7.18
   - 原因：Spring Boot 4.0.0 需要 Java 17+，不支持 Java 8

3. **Spring Framework 版本**
   - 自动调整为：5.3.31（由 Spring Boot 2.7.18 管理）

4. **Spring Cloud 版本调整**
   - 从：2025.1.0
   - 到：2021.0.10
   - 原因：适配 Spring Boot 2.7.18

5. **MyBatis Spring Boot Starter 版本调整**
   - 从：3.0.3
   - 到：2.3.2
   - 原因：适配 Spring Boot 2.7.18

6. **PageHelper 版本调整**
   - 从：1.4.2
   - 到：1.4.7
   - 原因：更好的兼容性

7. **代码调整**
   - `jakarta.annotation.Resource` → `javax.annotation.Resource`
   - 原因：Spring Boot 2.7.x 使用 `javax.*` 包

8. **依赖名称调整**
   - `spring-boot-starter-webmvc` → `spring-boot-starter-web`
   - `spring-boot-starter-security-oauth2-client` → `spring-boot-starter-oauth2-client`
   - `spring-boot-starter-webmvc-test` → `spring-boot-starter-test`
   - 原因：Spring Boot 2.7.x 的依赖命名

9. **MySQL 驱动调整**
   - `mysql-connector-j` → `mysql-connector-java`
   - 原因：Spring Boot 2.7.x 使用 `mysql-connector-java`

10. **Spring Cloud 依赖调整**
    - 版本：2025.1.0 → 2021.0.10（已注释，如需要可取消注释）
    - 原因：适配 Spring Boot 2.7.18
    - 注意：如果不需要 Spring Cloud 功能，可以保持注释状态

11. **Integration 模块依赖调整**
    - `spring-boot-starter-kafka` → `spring-kafka`
    - `spring-boot-starter-restclient` → `spring-boot-starter-webflux`
    - 原因：
      - Spring Boot 2.7.x 中 Kafka 使用 `spring-kafka` 而不是 starter
      - `spring-boot-starter-restclient` 是 Spring Boot 3.2+ 才有的，2.7.x 使用 `spring-boot-starter-webflux` 中的 WebClient

**调整后的完整版本配置**:

```xml
<properties>
    <java.version>1.8</java.version>
    <spring-boot.version>2.7.18</spring-boot.version>
    <spring-cloud.version>2021.0.10</spring-cloud.version>
    <mybatis-spring-boot.version>2.3.2</mybatis-spring-boot.version>
    <pagehelper.version>1.4.7</pagehelper.version>
</properties>
```

---

## 依赖版本管理

### 当前项目依赖版本

```xml
<properties>
    <!-- Spring 相关 -->
    <spring-boot.version>2.7.18</spring-boot.version>
    <spring-cloud.version>2021.0.11</spring-cloud.version>
    
    <!-- MyBatis 相关 -->
    <mybatis.version>3.5.13</mybatis.version>
    <mybatis-spring-boot.version>2.3.2</mybatis-spring-boot.version>
    <tk-mybatis.version>4.2.3</tk-mybatis.version>
    <pagehelper.version>1.4.7</pagehelper.version>
</properties>
```

### 版本兼容性检查清单

- [x] Spring Boot 2.7.18 与 Java 8 兼容
- [x] Spring Framework 5.3.31 与 Java 8 兼容
- [x] MyBatis 3.5.13 与 Java 8 兼容
- [x] MyBatis Spring Boot Starter 2.3.2 与 Spring Boot 2.7.18 兼容
- [x] TK MyBatis 4.2.3 与 Spring Boot 2.7.18 兼容
- [x] PageHelper 1.4.7 与 Spring Boot 2.7.18 兼容
- [x] Spring Cloud 2021.0.11 与 Spring Boot 2.7.18 兼容

---

## 常见问题与解决方案

### Q1: Spring Boot 2.7.18 是否稳定？

**A**: Spring Boot 2.7.18 是 2.x 系列的最终版本，非常稳定：
- ✅ 生产环境广泛使用
- ✅ 官方支持至 2025 年
- ✅ 大量生产验证，稳定可靠
- ✅ 与 Java 8 完美兼容

### Q2: Java 8 vs Java 17 如何选择？

**A**: 
- **选择 Java 8**: 稳定、广泛使用、生态成熟（当前项目选择）
- **选择 Java 17**: 需要新特性、性能优化、长期支持

### Q3: 如何验证版本兼容性？

**A**: 
1. 查看 Spring Boot 官方文档
2. 使用 `mvn dependency:tree` 检查依赖
3. 运行测试套件验证
4. 查看依赖库的兼容性说明

### Q4: Spring Boot 2.7.x 使用 `javax.*` 还是 `jakarta.*`？

**A**: Spring Boot 2.7.x 使用 `javax.*` 包：
- ✅ 使用 `javax.servlet.*`、`javax.annotation.*` 等
- ❌ 不需要使用 `jakarta.*`（Spring Boot 3.0+ 才使用）
- 当前项目已正确配置为 `javax.*`

---

## 最佳实践

### 1. 版本选择原则

- ✅ **生产环境**: 使用 LTS 版本的 JDK 和稳定版本的 Spring Boot
- ✅ **新项目**: 可以使用最新版本，但要做好测试
- ✅ **团队协作**: 统一开发环境版本，避免兼容性问题

### 2. 版本管理建议

- 使用 Maven `dependencyManagement` 统一管理版本
- 定期检查依赖更新和安全补丁
- 保持主要版本一致（如所有模块使用相同的 Spring Boot 版本）

### 3. 测试验证

- 升级后运行完整的测试套件
- 进行性能测试对比
- 检查日志是否有废弃警告

### 4. 文档维护

- 记录版本升级日志
- 更新团队开发文档
- 标注已知问题和解决方案

---

## 参考资源

### 官方文档

- [Spring Boot 官方文档](https://spring.io/projects/spring-boot)
- [Spring Framework 官方文档](https://spring.io/projects/spring-framework)
- [Oracle JDK 文档](https://docs.oracle.com/en/java/)

### 版本兼容性

- [Spring Boot 版本支持矩阵](https://spring.io/projects/spring-boot#support)
- [Java 版本路线图](https://www.oracle.com/java/technologies/java-se-support-roadmap.html)

### 迁移指南

- [Spring Boot 3.0 迁移指南](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.0-Migration-Guide)
- [Java 17 新特性](https://openjdk.org/projects/jdk/17/)

---

## 更新记录

| 日期 | 版本 | 更新内容 | 作者 |
|------|------|---------|------|
| 2025-12-26 | 1.0 | 初始版本，记录 JDK、Spring、Spring Boot 版本适配关系 | lixiangyu |
| 2025-12-26 | 2.0 | 调整项目为 Java 8 + Spring Boot 2.7.18，更新所有版本配置和兼容性说明 | lixiangyu |

---

**文档版本**: 2.0  
**最后更新**: 2025-12-26  
**适用项目**: com.lixiangyu.demo  
**当前配置**: Java 8, Spring Boot 2.7.18, Spring Framework 5.3.31

