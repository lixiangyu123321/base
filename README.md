# Demo 项目

基于 Spring Boot 的多模块 Maven 项目，采用分层架构设计。

## 项目结构

```
demo/
├── common/          # 公共模块：工具类、常量、异常、配置
├── dal/             # 数据访问层：实体类、Mapper接口和XML
├── service/         # 业务服务层：业务逻辑实现
├── facade/          # 门面层：对外提供统一的服务接口
├── integration/     # 集成层：外部系统集成、消息队列、缓存等
├── web/             # Web层：控制器、启动类、配置
├── test/            # 测试模块：单元测试、集成测试
├── doc/             # 文档目录
└── sql/             # SQL脚本目录
```

## 技术栈

- **Java**: 8
- **Spring Boot**: 2.7.18
- **MyBatis**: 3.5.13
- **MyBatis Spring Boot Starter**: 2.3.2
- **TK MyBatis**: 4.2.3
- **PageHelper**: 1.4.7
- **Spring Cloud**: 2021.0.10
- **Lombok**: 最新版本
- **MySQL**: 8.0+

## 模块说明

### common 模块
公共基础模块，包含：
- 通用工具类（Result、PageResult）
- 常量定义（CommonConstants）
- 异常类（BusinessException）
- MyBatis 全局配置

### dal 模块
数据访问层，包含：
- 实体类（BaseDO、UserDO等）
- Mapper接口
- Mapper XML文件

### service 模块
业务服务层，包含：
- 服务接口定义
- 服务实现类
- 业务逻辑处理

### facade 模块
门面层，包含：
- 对外服务接口
- 门面实现类

### integration 模块
集成层，包含：
- 外部系统集成
- 消息队列配置
- 缓存配置
- 第三方服务调用

### web 模块
Web层，包含：
- Spring Boot 启动类
- Controller控制器
- 全局异常处理
- 配置文件

### test 模块
测试模块，包含：
- 单元测试
- 集成测试

## 快速开始

### 1. 环境要求

- JDK 17+
- Maven 3.6+
- MySQL 8.0+

### 2. 数据库初始化

执行 `sql/init.sql` 脚本创建数据库和表。

### 3. 配置修改

修改 `web/src/main/resources/application.yml` 中的数据库连接信息：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/demo?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: root
```

### 4. 运行项目

```bash
# 编译项目
mvn clean install

# 运行项目
cd web
mvn spring-boot:run
```

或者直接运行 `web` 模块的 `Application` 主类。

### 5. 访问接口

- 健康检查：http://localhost:8080/api/user/health
- 根据ID查询用户：http://localhost:8080/api/user/{id}
- 根据用户名查询用户：http://localhost:8080/api/user/username/{username}

## 开发规范

### 包名规范

- 所有包名以 `com.lixiangyu` 开头
- 各模块包结构：
  - `com.lixiangyu.common.*` - 公共模块
  - `com.lixiangyu.dal.*` - 数据访问层
  - `com.lixiangyu.service.*` - 业务服务层
  - `com.lixiangyu.facade.*` - 门面层
  - `com.lixiangyu.integration.*` - 集成层
  - `com.lixiangyu.controller.*` - 控制器层

### 代码规范

1. 所有实体类继承 `BaseDO`
2. 使用 Lombok 简化代码
3. 统一使用 `Result` 封装响应结果
4. 使用 `@RequiredArgsConstructor` 注入依赖
5. 事务注解使用 `@Transactional(rollbackFor = Exception.class)`

## 环境配置

项目支持多环境配置：

- `application.yml` - 默认配置
- `application-dev.yml` - 开发环境
- `application-prod.yml` - 生产环境

通过 `spring.profiles.active` 指定激活的环境。

## 许可证

MIT License

