# Maven 父子模块聚合详解

## 目录

1. [基本概念](#基本概念)
2. [父子模块关系](#父子模块关系)
3. [dependencies 与 dependencyManagement](#dependencies-与-dependencymanagement)
4. [版本号管理](#版本号管理)
5. [groupId 管理](#groupid-管理)
6. [模块聚合原理](#模块聚合原理)
7. [最佳实践](#最佳实践)
8. [常见问题](#常见问题)

---

## 基本概念

### 什么是父子模块？

Maven 父子模块（Parent-Child Modules）是一种项目组织方式，通过继承关系来管理多个相关模块。

- **父模块（Parent Module）**：通常是一个 `packaging=pom` 的聚合项目，用于统一管理子模块的版本、依赖、插件等配置
- **子模块（Child Module）**：继承父模块的配置，可以有自己的特定配置

### 父子模块的优势

1. **统一版本管理**：所有子模块共享相同的版本号
2. **依赖版本控制**：通过 `dependencyManagement` 统一管理依赖版本
3. **配置复用**：公共配置在父模块定义，子模块自动继承
4. **构建简化**：在父模块执行构建命令，可以一次性构建所有子模块

---

## 父子模块关系

### 父模块配置

父模块的 `pom.xml` 必须包含：

```xml
<project>
    <!-- 1. 定义自己的坐标 -->
    <groupId>com.lixiangyu</groupId>
    <artifactId>demo</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    
    <!-- 2. 声明 packaging 为 pom -->
    <packaging>pom</packaging>
    
    <!-- 3. 声明子模块列表 -->
    <modules>
        <module>common</module>
        <module>dal</module>
        <module>service</module>
        <module>facade</module>
        <module>integration</module>
        <module>web</module>
        <module>test</module>
    </modules>
</project>
```

**关键点：**
- `packaging` 必须设置为 `pom`，表示这是一个聚合项目，不会生成实际的 jar/war 包
- `<modules>` 标签列出所有子模块的目录名（不是 artifactId）

### 子模块配置

子模块的 `pom.xml` 必须包含：

```xml
<project>
    <!-- 1. 声明父模块 -->
    <parent>
        <groupId>com.lixiangyu</groupId>
        <artifactId>demo</artifactId>
        <version>0.0.1-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>
    
    <!-- 2. 只需要声明 artifactId（groupId 和 version 继承自父模块） -->
    <artifactId>common</artifactId>
    
    <!-- 3. 声明 packaging（通常是 jar） -->
    <packaging>jar</packaging>
</project>
```

**关键点：**
- `<parent>` 标签声明父模块的坐标
- `<relativePath>` 指定父模块 pom.xml 的相对路径（通常为 `../pom.xml`）
- 子模块**不需要**声明 `groupId` 和 `version`，会自动继承父模块的

---

## dependencies 与 dependencyManagement

### dependencies（直接依赖）

**作用：** 声明当前模块实际使用的依赖，这些依赖会被**直接引入**到项目中。

**特点：**
- 在父模块中使用：所有子模块**自动继承**这些依赖
- 在子模块中使用：仅当前模块使用

**父模块示例：**

```xml
<!-- 父模块 pom.xml -->
<dependencies>
    <!-- Lombok：所有模块都需要，所以在父模块声明 -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <version>1.8.3</version>>
        <optional>true</optional>
    </dependency>
</dependencies>
```

**说明：**
- 父模块中声明的 `<dependencies>` 会**强制传递**给所有子模块
- 如果某个依赖所有子模块都需要，应该在父模块的 `<dependencies>` 中声明
- `<optional>true</optional>` 表示该依赖是可选的，不会传递依赖

**子模块示例：**

```xml
<!-- dal 模块 pom.xml -->
<dependencies>
<!--   子模块无需引入父模块denpendencies引入的模块，直接继承-->
    <!-- 依赖 common 模块 -->
<!--    <dependency>-->
<!--        <groupId>com.lixiangyu</groupId>-->
<!--        <artifactId>common</artifactId>-->
<!--        &lt;!&ndash; 不需要写 version，由 dependencyManagement 管理 &ndash;&gt;-->
<!--    </dependency>-->
    
    <!-- MyBatis 依赖 -->
<!--    <dependency>-->
<!--        <groupId>tk.mybatis</groupId>-->
<!--        <artifactId>mapper</artifactId>-->
<!--        &lt;!&ndash; 不需要写 version，由 dependencyManagement 管理 &ndash;&gt;-->
<!--    </dependency>-->
</dependencies>
```

### dependencyManagement（依赖管理）

**作用：** 统一管理依赖的**版本号**，不直接引入依赖。子模块需要使用时，必须在自己的 `<dependencies>` 中声明，但可以**省略版本号**。

**特点：**
- 仅用于版本管理，不会实际引入依赖
- 子模块必须显式声明才能使用
- 子模块声明时可以省略 `version`，自动使用父模块定义的版本

**父模块示例：**

```xml
<!-- 父模块 pom.xml -->
<dependencyManagement>
    <dependencies>
        <!-- 项目内部模块版本管理 -->
        <dependency>
            <groupId>com.lixiangyu</groupId>
            <artifactId>common</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.lixiangyu</groupId>
            <artifactId>dal</artifactId>
            <version>${project.version}</version>
        </dependency>
        
        <!-- 第三方依赖版本管理 -->
        <dependency>
            <groupId>tk.mybatis</groupId>
            <artifactId>mapper</artifactId>
            <version>4.2.3</version>
        </dependency>
        <dependency>
            <groupId>org.mybatis</groupId>
            <artifactId>mybatis</artifactId>
            <version>3.5.13</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

**子模块使用：**

```xml
<!-- dal 模块 pom.xml -->
<dependencies>
    <!-- 使用 dependencyManagement 中定义的版本 -->
    <dependency>
        <groupId>tk.mybatis</groupId>
        <artifactId>mapper</artifactId>
        <!-- 版本号自动从父模块的 dependencyManagement 获取 -->
    </dependency>
</dependencies>
```

### 对比总结

| 特性 | dependencies | dependencyManagement |
|------|-------------|---------------------|
| **作用** | 直接引入依赖 | 仅管理版本号 |
| **是否引入依赖** | ✅ 是 | ❌ 否 |
| **子模块是否继承** | ✅ 自动继承 | ❌ 需显式声明 |
| **版本号** | 必须指定 | 可省略（由父模块管理） |
| **使用场景** | 所有模块都需要的依赖 | 统一管理依赖版本 |

---

## 版本号管理

### 什么时候需要加版本号？

#### 1. 父模块中

**必须加版本号的情况：**

```xml
<!-- 父模块 pom.xml -->
<project>
    <!-- ✅ 必须：定义自己的版本 -->
    <version>0.0.1-SNAPSHOT</version>
    
    <!-- ✅ 必须：dependencyManagement 中必须指定版本 -->
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>tk.mybatis</groupId>
                <artifactId>mapper</artifactId>
                <version>4.2.3</version>  <!-- ✅ 必须指定 -->
            </dependency>
        </dependencies>
    </dependencyManagement>
    
    <!-- ✅ 必须：dependencies 中必须指定版本（除非继承自父 POM） -->
    <dependencies>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <!-- 版本继承自 spring-boot-starter-parent -->
        </dependency>
    </dependencies>
</project>
```

#### 2. 子模块中

**不需要加版本号的情况：**

```xml
<!-- 子模块 pom.xml -->
<project>
    <parent>
        <groupId>com.lixiangyu</groupId>
        <artifactId>demo</artifactId>
        <version>0.0.1-SNAPSHOT</version>  <!-- ✅ 必须指定父模块版本 -->
    </parent>
    
    <!-- ❌ 不需要：artifactId 会自动继承父模块的 groupId 和 version -->
    <artifactId>common</artifactId>
    
    <dependencies>
        <!-- ❌ 不需要：版本由父模块的 dependencyManagement 管理 -->
        <dependency>
            <groupId>com.lixiangyu</groupId>
            <artifactId>common</artifactId>
            <!-- version 自动从 dependencyManagement 获取 -->
        </dependency>
        
        <!-- ❌ 不需要：版本继承自 spring-boot-starter-parent -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
            <!-- version 继承自父 POM -->
        </dependency>
    </dependencies>
</project>
```

**需要加版本号的情况：**

```xml
<!-- 子模块 pom.xml -->
<dependencies>
    <!-- ✅ 需要：如果父模块的 dependencyManagement 中没有定义，必须指定版本 -->
    <dependency>
        <groupId>com.example</groupId>
        <artifactId>some-library</artifactId>
        <version>1.0.0</version>  <!-- ✅ 必须指定 -->
    </dependency>
    
    <!-- ✅ 需要：如果要覆盖父模块定义的版本 -->
    <dependency>
        <groupId>tk.mybatis</groupId>
        <artifactId>mapper</artifactId>
        <version>4.2.4</version>  <!-- ✅ 覆盖父模块的 4.2.3 -->
    </dependency>
</dependencies>
```

### 版本号继承规则

1. **子模块的版本号**：自动继承父模块的 `<version>`
2. **依赖的版本号**：按以下优先级查找
   - 子模块 `<dependencies>` 中显式指定的版本（最高优先级）
   - 父模块 `<dependencyManagement>` 中定义的版本
   - 父 POM（如 `spring-boot-starter-parent`）中定义的版本
   - 如果都找不到，Maven 会报错

---

## groupId 管理

### 什么时候需要加 groupId？

#### 1. 父模块中

**必须加 groupId：**

```xml
<!-- 父模块 pom.xml -->
<project>
    <!-- ✅ 必须：定义自己的 groupId -->
    <groupId>com.lixiangyu</groupId>
    <artifactId>demo</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</project>
```

#### 2. 子模块中

**不需要加 groupId：**

```xml
<!-- 子模块 pom.xml -->
<project>
    <parent>
        <groupId>com.lixiangyu</groupId>
        <artifactId>demo</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>
    
    <!-- ❌ 不需要：groupId 自动继承父模块 -->
    <artifactId>common</artifactId>
    <!-- groupId 自动为 com.lixiangyu -->
</project>
```

**需要加 groupId 的情况（不推荐）：**

```xml
<!-- 子模块 pom.xml -->
<project>
    <parent>
        <groupId>com.lixiangyu</groupId>
        <artifactId>demo</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>
    
    <!-- ⚠️ 不推荐：如果子模块的 groupId 与父模块不同，需要显式声明 -->
    <groupId>com.other.company</groupId>
    <artifactId>common</artifactId>
</project>
```

**最佳实践：** 所有子模块应该使用相同的 `groupId`，继承父模块的 `groupId`，这样更符合 Maven 规范。

#### 3. 依赖声明中

**必须加 groupId：**

```xml
<dependencies>
    <!-- ✅ 必须：所有依赖都必须指定 groupId -->
    <dependency>
        <groupId>com.lixiangyu</groupId>  <!-- ✅ 必须 -->
        <artifactId>common</artifactId>
    </dependency>
    
    <dependency>
        <groupId>org.springframework.boot</groupId>  <!-- ✅ 必须 -->
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
</dependencies>
```

---

## 模块聚合原理

### 聚合（Aggregation）vs 继承（Inheritance）

Maven 支持两种多模块组织方式：

1. **聚合（Aggregation）**：父模块通过 `<modules>` 声明子模块，用于统一构建
2. **继承（Inheritance）**：子模块通过 `<parent>` 声明父模块，用于配置继承

**子模块可继承的内容包括：**

1. **坐标信息**
   - `groupId`：自动继承父模块的 groupId
   - `version`：自动继承父模块的 version
   - `artifactId`：必须自己声明（不能继承）

2. **属性（Properties）**
   - 父模块 `<properties>` 中定义的所有属性
   - 子模块可以直接使用 `${属性名}` 引用

3. **依赖管理（Dependency Management）**
   - `<dependencyManagement>` 中定义的依赖版本
   - 子模块声明依赖时可以省略版本号

4. **直接依赖（Dependencies）**
   - 父模块 `<dependencies>` 中声明的依赖
   - 所有子模块自动继承这些依赖

5. **插件管理（Plugin Management）**
   - `<pluginManagement>` 中定义的插件配置
   - 子模块使用插件时可以省略版本号

6. **插件配置（Plugins）**
   - 父模块 `<build><plugins>` 中声明的插件
   - 子模块自动继承插件配置

7. **构建配置（Build Configuration）**
   - `<build>` 中的配置（如源码目录、资源目录等）
   - 子模块可以覆盖父模块的配置

8. **报告配置（Reporting）**
   - `<reporting>` 中的配置（如测试报告、代码覆盖率等）

9. **仓库配置（Repositories）**
   - `<repositories>` 和 `<pluginRepositories>` 中定义的仓库
   - 子模块自动继承仓库配置

10. **分发管理（Distribution Management）**
    - `<distributionManagement>` 中的配置（如部署仓库地址）

11. **开发者信息（Developers）**
    - `<developers>` 中定义的开发者信息

12. **许可证信息（Licenses）**
    - `<licenses>` 中定义的许可证信息

**子模块不能继承的内容：**

- `artifactId`：必须自己声明
- `packaging`：必须自己声明（通常为 jar）
- `name`：必须自己声明
- `description`：必须自己声明
- `<modules>`：子模块不能再声明子模块（除非是嵌套的多级模块）

**实际项目中，通常同时使用两种方式：**

```xml
<!-- 父模块：同时实现聚合和继承 -->
<project>
    <!-- 聚合：声明子模块列表 -->
    <modules>
        <module>common</module>
        <module>dal</module>
    </modules>
    
    <!-- 继承：定义公共配置供子模块继承 -->
    <properties>
        <java.version>17</java.version>
    </properties>
    
    <dependencyManagement>
        <dependencies>
            <!-- 版本管理 -->
        </dependencies>
    </dependencyManagement>
</project>
```

```xml
<!-- 子模块：继承父模块配置 -->
<project>
    <!-- 继承：声明父模块 -->
    <parent>
        <groupId>com.lixiangyu</groupId>
        <artifactId>demo</artifactId>
        <version>0.0.1-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>
    
    <artifactId>common</artifactId>
</project>
```

### 构建顺序

Maven 会根据模块依赖关系自动确定构建顺序：

```
父模块 (demo)
  ├── common (无依赖，最先构建)
  ├── dal (依赖 common)
  ├── service (依赖 dal, common)
  ├── facade (依赖 service, dal, common)
  ├── integration (依赖 service, dal, common)
  ├── web (依赖 service, facade, common)
  └── test (依赖所有模块，最后构建)
```

**构建命令：**

```bash
# 在父模块目录执行，会按顺序构建所有子模块
mvn clean install

# 只构建某个子模块（会自动构建其依赖的模块）
cd dal
mvn clean install
```

### relativePath 的作用

```xml
<parent>
    <groupId>com.lixiangyu</groupId>
    <artifactId>demo</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <relativePath>../pom.xml</relativePath>  <!-- 指定父模块路径 -->
</parent>
```

**说明：**
- `<relativePath>` 指定父模块 pom.xml 的相对路径
- 如果省略，Maven 会先查找本地仓库，再查找相对路径 `../pom.xml`
- **推荐显式指定**，避免查找本地仓库，提高构建速度

---

## 最佳实践

### 1. 父模块配置规范

```xml
<!-- 父模块 pom.xml -->
<project>
    <!-- ✅ 定义坐标 -->
    <groupId>com.lixiangyu</groupId>
    <artifactId>demo</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <packaging>pom</packaging>
    
    <!-- ✅ 声明子模块 -->
    <modules>
        <module>common</module>
        <module>dal</module>
    </modules>
    
    <!-- ✅ 定义属性 -->
    <properties>
        <java.version>17</java.version>
        <project.version>0.0.1-SNAPSHOT</project.version>
    </properties>
    
    <!-- ✅ 所有模块都需要的依赖 -->
    <dependencies>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
    </dependencies>
    
    <!-- ✅ 统一管理依赖版本 -->
    <dependencyManagement>
        <dependencies>
            <!-- 项目内部模块 -->
            <dependency>
                <groupId>com.lixiangyu</groupId>
                <artifactId>common</artifactId>
                <version>${project.version}</version>
            </dependency>
            <!-- 第三方依赖 -->
            <dependency>
                <groupId>tk.mybatis</groupId>
                <artifactId>mapper</artifactId>
                <version>4.2.3</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

### 2. 子模块配置规范

```xml
<!-- 子模块 pom.xml -->
<project>
    <!-- ✅ 声明父模块，指定 relativePath -->
    <parent>
        <groupId>com.lixiangyu</groupId>
        <artifactId>demo</artifactId>
        <version>0.0.1-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>
    
    <!-- ✅ 只声明 artifactId（groupId 和 version 继承） -->
    <artifactId>common</artifactId>
    <packaging>jar</packaging>
    
    <!-- ✅ 声明依赖，不写版本（由 dependencyManagement 管理） -->
    <dependencies>
        <dependency>
            <groupId>com.lixiangyu</groupId>
            <artifactId>common</artifactId>
            <!-- version 自动从 dependencyManagement 获取 -->
        </dependency>
    </dependencies>
</project>
```

### 3. 依赖声明规范

**✅ 推荐做法：**

```xml
<!-- 子模块中 -->
<dependencies>
    <!-- 项目内部模块：groupId 必须写，version 不写 -->
    <dependency>
        <groupId>com.lixiangyu</groupId>
        <artifactId>common</artifactId>
    </dependency>
    
    <!-- 第三方依赖：groupId 必须写，version 不写（由 dependencyManagement 管理） -->
    <dependency>
        <groupId>tk.mybatis</groupId>
        <artifactId>mapper</artifactId>
    </dependency>
</dependencies>
```

**❌ 不推荐做法：**

```xml
<!-- ❌ 不要在子模块中重复声明 groupId 和 version -->
<project>
    <groupId>com.lixiangyu</groupId>  <!-- ❌ 冗余 -->
    <artifactId>common</artifactId>
    <version>0.0.1-SNAPSHOT</version>  <!-- ❌ 冗余 -->
</project>

<!-- ❌ 不要在子模块中重复指定版本（除非要覆盖） -->
<dependencies>
    <dependency>
        <groupId>tk.mybatis</groupId>
        <artifactId>mapper</artifactId>
        <version>4.2.3</version>  <!-- ❌ 冗余，父模块已管理 -->
    </dependency>
</dependencies>
```

### 4. 版本管理规范

**使用属性统一管理版本：**

```xml
<!-- 父模块 -->
<properties>
    <project.version>0.0.1-SNAPSHOT</project.version>
    <mybatis.version>3.5.13</mybatis.version>
    <tk-mybatis.version>4.2.3</tk-mybatis.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.lixiangyu</groupId>
            <artifactId>common</artifactId>
            <version>${project.version}</version>  <!-- ✅ 使用属性 -->
        </dependency>
        <dependency>
            <groupId>tk.mybatis</groupId>
            <artifactId>mapper</artifactId>
            <version>${tk-mybatis.version}</version>  <!-- ✅ 使用属性 -->
        </dependency>
    </dependencies>
</dependencyManagement>
```

---

## 常见问题

### Q1: 子模块需要声明 groupId 和 version 吗？

**A:** 不需要。子模块会自动继承父模块的 `groupId` 和 `version`。只有在子模块的 `groupId` 与父模块不同时（不推荐），才需要显式声明。

### Q2: 子模块的依赖需要写版本号吗？

**A:** 通常不需要。如果父模块的 `dependencyManagement` 中已经定义了版本，子模块可以省略版本号。只有在以下情况才需要写版本号：
- 父模块的 `dependencyManagement` 中没有定义
- 需要覆盖父模块定义的版本

### Q3: dependencies 和 dependencyManagement 的区别？

**A:** 
- `dependencies`：直接引入依赖，父模块中的依赖会自动传递给所有子模块
- `dependencyManagement`：仅管理版本号，不引入依赖，子模块需要显式声明才能使用

### Q4: 父模块的 dependencies 和子模块的 dependencies 有什么区别？

**A:**
- **父模块的 dependencies**：所有子模块自动继承这些依赖
- **子模块的 dependencies**：仅当前模块使用，不会传递给其他模块

### Q5: 如何统一管理所有模块的版本？

**A:** 使用 `<properties>` 定义版本属性，然后在 `dependencyManagement` 中使用 `${属性名}` 引用：

```xml
<properties>
    <project.version>0.0.1-SNAPSHOT</project.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.lixiangyu</groupId>
            <artifactId>common</artifactId>
            <version>${project.version}</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### Q6: relativePath 的作用是什么？

**A:** `relativePath` 指定父模块 pom.xml 的相对路径。如果省略，Maven 会先查找本地仓库，再查找默认相对路径。**推荐显式指定**，提高构建速度。

### Q7: 子模块之间如何相互依赖？

**A:** 在子模块的 `<dependencies>` 中声明其他子模块的依赖，版本号由父模块的 `dependencyManagement` 管理：

```xml
<!-- service 模块依赖 dal 模块 -->
<dependencies>
    <dependency>
        <groupId>com.lixiangyu</groupId>
        <artifactId>dal</artifactId>
        <!-- version 自动从 dependencyManagement 获取 -->
    </dependency>
</dependencies>
```

---

## 总结

### 核心原则

1. **父模块**：定义公共配置、版本管理、所有模块都需要的依赖
2. **子模块**：继承父模块配置，只声明自己的特定依赖
3. **版本管理**：统一在父模块的 `dependencyManagement` 中管理
4. **groupId 管理**：子模块自动继承父模块的 `groupId`，无需重复声明

### 配置检查清单

**父模块：**
- ✅ 声明 `packaging=pom`
- ✅ 声明 `<modules>` 列表
- ✅ 定义 `<properties>` 统一管理版本
- ✅ 在 `<dependencies>` 中声明所有模块都需要的依赖
- ✅ 在 `<dependencyManagement>` 中统一管理依赖版本

**子模块：**
- ✅ 声明 `<parent>` 并指定 `relativePath`
- ✅ 只声明 `<artifactId>`（不声明 `groupId` 和 `version`）
- ✅ 在 `<dependencies>` 中声明依赖，不写版本号（由 `dependencyManagement` 管理）

---

**文档版本：** 1.0  
**最后更新：** 2024年  
**适用项目：** com.lixiangyu.demo

