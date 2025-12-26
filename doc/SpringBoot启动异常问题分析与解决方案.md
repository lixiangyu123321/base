# Spring Boot 启动异常问题分析与解决方案

## 文档信息

- **创建日期**: 2025-12-26
- **问题类型**: Spring Boot 启动异常、MyBatis 配置问题、Lombok 编译问题
- **影响范围**: web 模块、dal 模块、全局异常处理器

---

## 问题概述

在启动 Spring Boot 应用时，遇到了多个启动异常，主要包括：

1. **MyBatis Mapper Bean 创建失败**：`Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required`
2. **Lombok 编译错误**：`找不到符号: 变量 log`

---

## 问题一：MyBatis Mapper Bean 创建失败

### 错误信息

```
org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'evaluatingMapper': 
Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required
```

### 问题分析

#### 1.1 根本原因

MyBatis 的 Mapper 接口无法被正确扫描和初始化，导致 `sqlSessionFactory` 或 `sqlSessionTemplate` 无法注入到 Mapper Bean 中。

#### 1.2 具体原因分析

**原因 1：web 模块缺少 dal 模块依赖**

- **问题描述**：web 模块的 `pom.xml` 中没有显式依赖 `dal` 模块
- **影响**：web 模块无法访问 dal 模块中的 Mapper 接口和 XML 文件
- **错误表现**：MyBatis 无法找到 Mapper 接口，导致 Bean 创建失败

**原因 2：MyBatis Mapper XML 路径配置错误**

- **问题描述**：`application.yml` 中的 `mapper-locations` 配置路径不正确
- **配置错误**：
  ```yaml
  mapper-locations: classpath:mapper/**/*.xml
  ```
- **实际情况**：Mapper XML 文件实际在 `dal/src/main/resources/mybatis/mapper/` 目录下
- **影响**：MyBatis 无法找到 Mapper XML 文件，导致 SQL 映射失败

**原因 3：EvaluatingMapper.xml 中的表名错误**

- **问题描述**：批量更新 SQL 中使用了错误的表名
- **错误代码**：
  ```xml
  UPDATE evaluating
  ```
- **正确表名**：应该是 `t_aigc_evaluating`
- **影响**：SQL 执行时会报表不存在的错误

**原因 4：EvaluatingMapper.xml 中 remark 字段更新逻辑错误**

- **问题描述**：批量更新 SQL 中 remark 字段的 CASE WHEN 语句有误
- **错误代码**：
  ```xml
  WHEN id = #{item.remark} THEN #{item.remark}
  ```
- **正确代码**：
  ```xml
  WHEN id = #{item.id} THEN #{item.remark}
  ```
- **影响**：SQL 语法错误，无法正确更新数据

**原因 5：缺少 MySQL 驱动依赖**

- **问题描述**：web 模块的 `pom.xml` 中缺少 MySQL 数据库驱动
- **影响**：无法连接数据库，MyBatis 无法初始化数据源

**原因 6：web 模块缺少 mybatis-spring-boot-starter 依赖（核心问题）**

- **问题描述**：web 模块的 `pom.xml` 中没有显式依赖 `mybatis-spring-boot-starter`
- **根本原因**：
  - Spring Boot 的自动配置机制只会扫描**启动模块（web）的 classpath**
  - 虽然 dal 模块有 `mybatis-spring-boot-starter` 依赖，但依赖传递不能触发自动配置
  - `MybatisAutoConfiguration` 自动配置类需要检测到 `mybatis-spring-boot-starter` 在启动模块的 classpath 中才会生效
  - 没有自动配置，就不会创建 `SqlSessionFactory` 和 `SqlSessionTemplate` Bean
- **影响**：MyBatis 自动配置不生效，无法创建 `SqlSessionFactory`，导致 Mapper Bean 创建失败
- **错误表现**：`Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required`

### 解决方案

#### 解决方案 1：添加 dal 模块依赖

**文件**: `web/pom.xml`

**修改前**:
```xml
<dependencies>
    <dependency>
        <groupId>com.lixiangyu</groupId>
        <artifactId>service</artifactId>
    </dependency>
    <dependency>
        <groupId>com.lixiangyu</groupId>
        <artifactId>facade</artifactId>
    </dependency>
    <dependency>
        <groupId>com.lixiangyu</groupId>
        <artifactId>common</artifactId>
    </dependency>
</dependencies>
```

**修改后**:
```xml
<dependencies>
    <dependency>
        <groupId>com.lixiangyu</groupId>
        <artifactId>service</artifactId>
    </dependency>
    <dependency>
        <groupId>com.lixiangyu</groupId>
        <artifactId>facade</artifactId>
    </dependency>
    <dependency>
        <groupId>com.lixiangyu</groupId>
        <artifactId>dal</artifactId>  <!-- 新增 -->
    </dependency>
    <dependency>
        <groupId>com.lixiangyu</groupId>
        <artifactId>common</artifactId>
    </dependency>
</dependencies>
```

**说明**：web 模块作为启动模块，需要直接依赖 dal 模块才能访问 Mapper 接口和 XML 文件。

#### 解决方案 2：修正 MyBatis Mapper XML 路径配置

**文件**: `web/src/main/resources/application.yml`

**修改前**:
```yaml
mybatis:
  mapper-locations: classpath:mapper/**/*.xml
```

**修改后**:
```yaml
mybatis:
  mapper-locations: classpath:mybatis/mapper/**/*.xml
```

**说明**：Mapper XML 文件实际在 `dal/src/main/resources/mybatis/mapper/` 目录下，需要匹配正确的路径。

#### 解决方案 3：修正 EvaluatingMapper.xml 中的表名

**文件**: `dal/src/main/resources/mybatis/mapper/EvaluatingMapper.xml`

**修改前**:
```xml
<update id="batchUpdate">
    UPDATE evaluating
    ...
</update>
```

**修改后**:
```xml
<update id="batchUpdate">
    UPDATE t_aigc_evaluating
    ...
</update>
```

**说明**：确保 SQL 中的表名与数据库中的实际表名一致。

#### 解决方案 4：修正 remark 字段更新逻辑

**文件**: `dal/src/main/resources/mybatis/mapper/EvaluatingMapper.xml`

**修改前**:
```xml
<trim prefix="remark = CASE" suffix="END,">
    <foreach collection="dosToUpdate" item="item" index="index">
        <if test="item.remark != null">
            WHEN id = #{item.remark} THEN #{item.remark}  <!-- 错误 -->
        </if>
    </foreach>
</trim>
```

**修改后**:
```xml
<trim prefix="remark = CASE" suffix="END,">
    <foreach collection="dosToUpdate" item="item" index="index">
        <if test="item.remark != null">
            WHEN id = #{item.id} THEN #{item.remark}  <!-- 正确 -->
        </if>
    </foreach>
</trim>
```

**说明**：CASE WHEN 语句中，WHEN 条件应该使用 `id` 字段进行比较，而不是 `remark` 字段。

#### 解决方案 5：添加 MySQL 驱动依赖

**文件**: `web/pom.xml`

**修改后**:
```xml
<dependencies>
    <!-- 数据库驱动（MyBatis 需要） -->
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
    </dependency>
</dependencies>
```

**说明**：MySQL 驱动是 MyBatis 连接数据库的必需依赖。

#### 解决方案 6：添加 mybatis-spring-boot-starter 依赖（核心解决方案）

**文件**: `web/pom.xml`

**修改后**:
```xml
<dependencies>
    <!-- 数据库驱动（MyBatis 需要） -->
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
    </dependency>
    
    <!-- MyBatis Spring Boot Starter（启动模块需要显式依赖） -->
    <dependency>
        <groupId>org.mybatis.spring.boot</groupId>
        <artifactId>mybatis-spring-boot-starter</artifactId>
    </dependency>
</dependencies>
```

**说明**：
1. **为什么启动模块需要显式依赖**：
   - Spring Boot 的自动配置机制基于 `spring.factories` 文件
   - 自动配置类只会扫描启动模块的 classpath
   - 即使其他模块（如 dal）有该依赖，也不会触发自动配置
   
2. **MyBatis 自动配置的工作原理**：
   ```
   mybatis-spring-boot-starter
   └── META-INF/spring.factories
       └── org.springframework.boot.autoconfigure.EnableAutoConfiguration
           └── org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration
   ```
   - `MybatisAutoConfiguration` 需要检测到 `mybatis-spring-boot-starter` 在 classpath 中
   - 然后才会创建 `SqlSessionFactory` 和 `SqlSessionTemplate` Bean
   - 这些 Bean 是 Mapper 接口创建的基础
   
3. **依赖传递 vs 自动配置**：
   - Maven 依赖传递：dal 模块的依赖可以传递到 web 模块（编译时可见）
   - Spring Boot 自动配置：只扫描启动模块的 classpath（运行时生效）
   - **关键区别**：自动配置需要依赖在启动模块的 classpath 中，而不仅仅是依赖传递

---

## 问题二：Lombok 编译错误

### 错误信息

```
java: 找不到符号
  符号:   变量 log
  位置: 类 com.lixiangyu.controller.GlobalExceptionHandler
```

### 问题分析

#### 2.1 根本原因

虽然类上已经添加了 `@Slf4j` 注解，但 Lombok 注解处理器在编译时没有正确工作，导致 `log` 变量没有被生成。

#### 2.2 具体原因

**原因：web 模块缺少 Lombok 注解处理器配置**

- **问题描述**：web 模块的 `pom.xml` 中 `maven-compiler-plugin` 没有配置 Lombok 的注解处理器路径
- **影响**：编译时 Lombok 无法处理 `@Slf4j` 注解，导致 `log` 变量无法生成
- **表现**：IDE 和 Maven 编译时都报错，提示找不到 `log` 变量

### 解决方案

#### 解决方案：配置 Lombok 注解处理器

**文件**: `web/pom.xml`

**修改前**:
```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

**修改后**:
```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
                <annotationProcessorPaths>
                    <path>
                        <groupId>org.projectlombok</groupId>
                        <artifactId>lombok</artifactId>
                        <version>1.18.30</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

**说明**：
1. 虽然父模块已经在 `<dependencies>` 中声明了 Lombok，但为了确保编译时 Lombok 注解处理器能正确工作，需要在 `maven-compiler-plugin` 中显式配置注解处理器路径
2. 这样可以确保 Maven 编译时 Lombok 能够正确处理 `@Slf4j`、`@Data` 等注解

---

## 问题总结

### 问题分类

| 问题类型 | 问题数量 | 严重程度 |
|---------|---------|---------|
| 依赖配置问题 | 3 | 高 |
| 配置文件问题 | 1 | 高 |
| SQL 语法问题 | 2 | 中 |
| 编译配置问题 | 1 | 中 |

**核心问题**：web 模块缺少 `mybatis-spring-boot-starter` 依赖（原因 6），这是导致 `sqlSessionFactory` 缺失的根本原因。

### 修复文件清单

1. `web/pom.xml` - 添加 dal 模块依赖、MySQL 驱动、**mybatis-spring-boot-starter 依赖**、Lombok 注解处理器配置
2. `web/src/main/resources/application.yml` - 修正 MyBatis Mapper XML 路径
3. `dal/src/main/resources/mybatis/mapper/EvaluatingMapper.xml` - 修正表名和 SQL 语法错误

**关键修复**：在 web 模块中添加 `mybatis-spring-boot-starter` 依赖是解决 `sqlSessionFactory` 缺失问题的核心。

### 验证步骤

修复后，按以下步骤验证：

1. **清理项目**：
   ```bash
   mvn clean
   ```

2. **重新编译**：
   ```bash
   mvn compile
   ```

3. **启动应用**：
   ```bash
   mvn spring-boot:run
   ```

4. **检查日志**：
   - 确认没有 Bean 创建异常
   - 确认 MyBatis Mapper 扫描成功
   - 确认应用正常启动

---

## 预防措施

### 1. 模块依赖管理

- **原则**：启动模块（web）必须显式依赖所有需要使用的模块
- **检查**：定期检查模块依赖关系，确保依赖链完整
- **工具**：使用 `mvn dependency:tree` 查看依赖树

### 2. 配置文件管理

- **原则**：配置文件路径必须与实际文件位置一致
- **检查**：使用相对路径时，确保路径正确
- **工具**：使用 IDE 的路径自动补全功能

### 3. SQL 文件管理

- **原则**：SQL 中的表名、字段名必须与数据库实际结构一致
- **检查**：编写 SQL 后，先在数据库中验证
- **工具**：使用数据库管理工具验证 SQL 语法

### 4. Lombok 使用规范

- **原则**：使用 Lombok 注解时，确保编译配置正确
- **检查**：每个模块的 `pom.xml` 中都应该配置 Lombok 注解处理器
- **工具**：使用 IDE 的 Lombok 插件，确保注解生效

### 5. 多模块项目最佳实践

1. **依赖传递**：
   - 父模块在 `<dependencies>` 中声明的依赖会传递给所有子模块
   - 但设置了 `<optional>true</optional>` 的依赖不会传递
   - 启动模块需要显式声明所有必需的依赖

2. **资源文件路径**：
   - 资源文件路径是相对于 `src/main/resources` 的
   - 跨模块访问资源时，需要确保模块依赖正确

3. **编译顺序**：
   - Maven 会根据模块依赖关系自动确定编译顺序
   - 确保被依赖的模块先编译

---

## 相关文档

- [Maven 父子模块聚合详解](./Maven父子模块聚合详解.md)
- [MyBatis 配置相关](./mybatis配置相关.md)
- [项目 README](../README.md)

---

## 更新记录

| 日期 | 版本 | 更新内容 | 作者 |
|------|------|---------|------|
| 2025-12-26 | 1.0 | 初始版本，记录 MyBatis 启动异常和 Lombok 编译问题 | lixiangyu |
| 2025-12-26 | 1.1 | 添加核心问题：web 模块缺少 mybatis-spring-boot-starter 依赖，详细说明 Spring Boot 自动配置原理和依赖传递的区别 | lixiangyu |

---

**文档版本**: 1.1  
**最后更新**: 2025-12-26  
**适用项目**: com.lixiangyu.demo

