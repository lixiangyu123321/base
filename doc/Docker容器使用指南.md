# Docker 容器使用指南

## 文档信息

- **创建日期**: 2025-01-27
- **配置文件**: `docker/docker-compose.yml`
- **网络名称**: `docker-cluster-network`

---

## 一、快速启动

### 1. 启动所有容器

```bash
# 进入docker目录
cd docker

# 启动所有服务
docker-compose up -d

# 查看运行状态
docker-compose ps

# 查看日志
docker-compose logs -f
```

### 2. 启动指定服务

```bash
# 只启动MySQL主从
docker-compose up -d mysql-master mysql-slave

# 只启动Redis集群
docker-compose up -d redis-node1 redis-node2 redis-node3

# 只启动Nacos
docker-compose up -d nacos
```

### 3. 停止和清理

```bash
# 停止所有服务
docker-compose stop

# 停止并删除容器
docker-compose down

# 停止并删除容器、数据卷（谨慎使用，会删除数据）
docker-compose down -v
```

---

## 二、MySQL 数据库连接

### 1. MySQL 主库（mysql-master）

**连接信息**:
- **主机地址**: `localhost` 或 `127.0.0.1`
- **端口**: `3307`（映射到容器内的3306）
- **数据库名**: `test_db`（自动创建）
- **用户名**: 
  - `root` / 密码: `123456`
  - `test_user` / 密码: `123456`
- **时区**: `Asia/Shanghai`

**JDBC连接字符串**:
```
jdbc:mysql://localhost:3307/test_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
```

**在 application.yml 中的配置**:
```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3307/test_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: 123456
    hikari:
      minimum-idle: 5
      maximum-pool-size: 20
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

**使用数据库客户端连接**:
- **Navicat/DataGrip/DBeaver**:
  - Host: `localhost`
  - Port: `3307`
  - Username: `root`
  - Password: `123456`
  - Database: `test_db`

**容器内连接**（容器间通信）:
- **主机地址**: `mysql-master`（容器名称，在同一Docker网络中）
- **端口**: `3306`（容器内部端口）
- **JDBC连接字符串**: `jdbc:mysql://mysql-master:3306/test_db?...`

---

### 2. MySQL 从库（mysql-slave）

**连接信息**:
- **主机地址**: `localhost` 或 `127.0.0.1`
- **端口**: `3308`（映射到容器内的3306）
- **用户名**: `root` / 密码: `123456`
- **注意**: 从库默认是只读模式（`read-only=1`）

**JDBC连接字符串**:
```
jdbc:mysql://localhost:3308/test_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true&readOnly=true
```

**在 application.yml 中的配置**（读写分离场景）:
```yaml
spring:
  datasource:
    # 主库配置（写操作）
    master:
      driver-class-name: com.mysql.cj.jdbc.Driver
      url: jdbc:mysql://localhost:3307/test_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false
      username: root
      password: 123456
    # 从库配置（读操作）
    slave:
      driver-class-name: com.mysql.cj.jdbc.Driver
      url: jdbc:mysql://localhost:3308/test_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&readOnly=true
      username: root
      password: 123456
```

**容器内连接**:
- **主机地址**: `mysql-slave`
- **端口**: `3306`

---

### 3. MySQL 主从复制配置

**主从复制状态检查**:

```bash
# 进入主库容器
docker exec -it mysql-master mysql -uroot -p123456

# 查看主库状态
SHOW MASTER STATUS;

# 进入从库容器
docker exec -it mysql-slave mysql -uroot -p123456

# 查看从库状态
SHOW SLAVE STATUS\G;
```

**手动配置主从复制**（如果未自动配置）:

```sql
-- 在从库中执行
CHANGE MASTER TO
  MASTER_HOST='mysql-master',
  MASTER_PORT=3306,
  MASTER_USER='root',
  MASTER_PASSWORD='123456',
  MASTER_LOG_FILE='mysql-bin.000001',
  MASTER_LOG_POS=0;

START SLAVE;
```

---

## 三、Redis 连接

### 1. Redis 节点1（redis-node1）

**连接信息**:
- **主机地址**: `localhost` 或 `127.0.0.1`
- **端口**: `6379`（客户端端口）
- **集群端口**: `16379`（集群总线端口）
- **密码**: 无（根据redis.conf配置）

**在 application.yml 中的配置**:
```yaml
spring:
  redis:
    host: localhost
    port: 6379
    password:  # 如果redis.conf中配置了密码，填写密码
    database: 0
    timeout: 3000
    lettuce:
      pool:
        max-active: 8
        max-idle: 8
        min-idle: 0
```

**Jedis连接示例**:
```java
Jedis jedis = new Jedis("localhost", 6379);
```

**容器内连接**:
- **主机地址**: `redis-node1`
- **端口**: `6379`

---

### 2. Redis 节点2（redis-node2）

**连接信息**:
- **主机地址**: `localhost`
- **端口**: `6380`
- **集群端口**: `16380`

**在 application.yml 中的配置**（多节点）:
```yaml
spring:
  redis:
    cluster:
      nodes:
        - localhost:6379
        - localhost:6380
        - localhost:6381
      max-redirects: 3
```

---

### 3. Redis 节点3（redis-node3）

**连接信息**:
- **主机地址**: `localhost`
- **端口**: `6381`
- **集群端口**: `16381`

---

### 4. Redis 集群模式连接

**Jedis集群连接示例**:
```java
Set<HostAndPort> jedisClusterNodes = new HashSet<>();
jedisClusterNodes.add(new HostAndPort("localhost", 6379));
jedisClusterNodes.add(new HostAndPort("localhost", 6380));
jedisClusterNodes.add(new HostAndPort("localhost", 6381));

JedisCluster jedisCluster = new JedisCluster(jedisClusterNodes);
```

**Spring Boot Redis集群配置**:
```yaml
spring:
  redis:
    cluster:
      nodes:
        - localhost:6379
        - localhost:6380
        - localhost:6381
      max-redirects: 3
    timeout: 3000
    lettuce:
      pool:
        max-active: 8
        max-idle: 8
        min-idle: 0
```

---

## 四、Elasticsearch 连接

### 1. ES 节点1（es-node1）

**连接信息**:
- **HTTP端口**: `9200`（REST API）
- **TCP端口**: `9300`（节点间通信）
- **集群名称**: `es-docker-cluster`
- **节点名称**: `es-node1`
- **安全认证**: 已禁用（`xpack.security.enabled=false`）

**REST API地址**:
```
http://localhost:9200
```

**在 application.yml 中的配置**:
```yaml
spring:
  elasticsearch:
    uris: http://localhost:9200
    connection-timeout: 1s
    socket-timeout: 30s
```

**Elasticsearch Java客户端配置**:
```java
RestClient restClient = RestClient.builder(
    new HttpHost("localhost", 9200, "http")
).build();
```

**容器内连接**:
- **HTTP地址**: `http://es-node1:9200`
- **TCP地址**: `es-node1:9300`

---

### 2. ES 节点2（es-node2）

**连接信息**:
- **HTTP端口**: `9201`
- **TCP端口**: `9301`

**REST API地址**:
```
http://localhost:9201
```

**集群连接配置**:
```yaml
spring:
  elasticsearch:
    uris: 
      - http://localhost:9200
      - http://localhost:9201
```

---

### 3. Elasticsearch 集群信息

**查看集群状态**:
```bash
# 查看集群健康状态
curl http://localhost:9200/_cluster/health?pretty

# 查看节点信息
curl http://localhost:9200/_cat/nodes?v

# 查看集群信息
curl http://localhost:9200/_cluster/stats?pretty
```

---

## 五、RabbitMQ 连接

### 1. RabbitMQ 节点1（rabbitmq-node1）

**连接信息**:
- **AMQP端口**: `5672`（消息队列协议）
- **管理界面端口**: `15672`（Web管理界面）
- **用户名**: `admin`
- **密码**: `123456`
- **管理界面URL**: `http://localhost:15672`

**在 application.yml 中的配置**:
```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: admin
    password: 123456
    virtual-host: /
    publisher-confirm-type: correlated
    publisher-returns: true
    listener:
      simple:
        acknowledge-mode: manual
        prefetch: 1
```

**Java连接示例**:
```java
ConnectionFactory factory = new ConnectionFactory();
factory.setHost("localhost");
factory.setPort(5672);
factory.setUsername("admin");
factory.setPassword("123456");
Connection connection = factory.newConnection();
```

**容器内连接**:
- **主机地址**: `rabbitmq-node1`
- **端口**: `5672`

---

### 2. RabbitMQ 节点2（rabbitmq-node2）

**连接信息**:
- **AMQP端口**: `5673`
- **管理界面端口**: `15673`
- **管理界面URL**: `http://localhost:15673`

**集群连接配置**:
```yaml
spring:
  rabbitmq:
    addresses: localhost:5672,localhost:5673
    username: admin
    password: 123456
```

---

### 3. RabbitMQ 管理界面

**访问管理界面**:
- **节点1**: http://localhost:15672
- **节点2**: http://localhost:15673
- **登录信息**: 
  - 用户名: `admin`
  - 密码: `123456`

**管理界面功能**:
- 查看队列、交换机、绑定关系
- 监控消息流量
- 管理用户和权限
- 查看集群状态

---

## 六、Nacos 配置中心连接

### 1. Nacos 服务连接

**连接信息**:
- **控制台端口**: `8848`（HTTP API和管理界面）
- **gRPC端口**: `9848`（客户端通信）
- **控制台URL**: `http://localhost:8848/nacos`
- **默认用户名**: `nacos`
- **默认密码**: `nacos`
- **运行模式**: `standalone`（单机模式）

**在 application.yml 中的配置**:
```yaml
spring:
  application:
    name: demo
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
        namespace: public
        group: DEFAULT_GROUP
      config:
        server-addr: localhost:8848
        file-extension: yaml
        namespace: public
        group: DEFAULT_GROUP
        shared-configs:
          - data-id: common-config.yaml
            group: DEFAULT_GROUP
            refresh: true
```

**bootstrap.yml 配置**（推荐）:
```yaml
spring:
  application:
    name: demo
  cloud:
    nacos:
      config:
        server-addr: localhost:8848
        file-extension: yaml
        namespace: public
        group: DEFAULT_GROUP
      discovery:
        server-addr: localhost:8848
        namespace: public
        group: DEFAULT_GROUP
```

**容器内连接**:
- **服务器地址**: `nacos:8848`

---

### 2. Nacos 数据库配置

**Nacos使用的MySQL连接**:
- **主机**: `mysql-master`（容器内）
- **端口**: `3306`
- **数据库**: `nacos_config`
- **用户名**: `root`
- **密码**: `123456`

**初始化Nacos数据库**:
```bash
# 进入MySQL主库容器
docker exec -it mysql-master mysql -uroot -p123456

# 创建nacos_config数据库
CREATE DATABASE IF NOT EXISTS nacos_config DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# 退出MySQL，执行初始化脚本（需要先下载nacos的SQL脚本）
# 脚本位置: https://github.com/alibaba/nacos/blob/master/distribution/conf/mysql-schema.sql
```

---

### 3. Nacos 使用示例

**访问Nacos控制台**:
1. 打开浏览器访问: http://localhost:8848/nacos
2. 使用默认账号登录: `nacos` / `nacos`
3. 在"配置管理"中创建配置
4. 在"服务管理"中查看注册的服务

**Java代码中使用Nacos配置**:
```java
@NacosValue(value = "${config.key:defaultValue}", autoRefreshed = true)
private String configValue;

@NacosPropertySource(dataId = "demo-config", autoRefreshed = true)
public class ConfigController {
    // ...
}
```

---

## 七、容器间网络通信

### 1. Docker网络说明

所有容器都在 `docker-cluster-network` 网络中，可以通过容器名称直接通信。

**容器名称与主机名映射**:
- `mysql-master` → MySQL主库
- `mysql-slave` → MySQL从库
- `redis-node1` → Redis节点1
- `redis-node2` → Redis节点2
- `redis-node3` → Redis节点3
- `es-node1` → Elasticsearch节点1
- `es-node2` → Elasticsearch节点2
- `rabbitmq-node1` → RabbitMQ节点1
- `rabbitmq-node2` → RabbitMQ节点2
- `nacos` → Nacos配置中心

---

### 2. 容器内连接地址

**如果您的应用也在Docker容器中运行**，使用以下地址：

```yaml
# MySQL
jdbc:mysql://mysql-master:3306/test_db

# Redis
redis:
  host: redis-node1
  port: 6379

# Elasticsearch
elasticsearch:
  uris: http://es-node1:9200

# RabbitMQ
rabbitmq:
  host: rabbitmq-node1
  port: 5672

# Nacos
nacos:
  server-addr: nacos:8848
```

---

### 3. 从宿主机连接

**如果您的应用在宿主机（Windows）上运行**，使用以下地址：

```yaml
# MySQL
jdbc:mysql://localhost:3307/test_db  # 主库
jdbc:mysql://localhost:3308/test_db  # 从库

# Redis
redis:
  host: localhost
  port: 6379  # 或 6380, 6381

# Elasticsearch
elasticsearch:
  uris: http://localhost:9200  # 或 9201

# RabbitMQ
rabbitmq:
  host: localhost
  port: 5672  # 或 5673

# Nacos
nacos:
  server-addr: localhost:8848
```

---

## 八、数据持久化

### 1. 数据卷映射

所有数据都持久化到Windows本地目录：

```
E:\docker-data\
├── mysql\
│   ├── master\    # MySQL主库数据
│   └── slave\     # MySQL从库数据
├── redis\
│   ├── node1\     # Redis节点1数据
│   ├── node2\     # Redis节点2数据
│   └── node3\     # Redis节点3数据
├── elasticsearch\
│   ├── node1\     # ES节点1数据
│   └── node2\     # ES节点2数据
├── rabbitmq\
│   ├── node1\     # RabbitMQ节点1数据
│   └── node2\     # RabbitMQ节点2数据
└── nacos\         # Nacos数据
```

**注意**: 请确保这些目录存在，或修改 `docker-compose.yml` 中的路径为您的实际路径。

---

### 2. 备份数据

```bash
# 备份MySQL数据
docker exec mysql-master mysqldump -uroot -p123456 test_db > backup.sql

# 备份Redis数据
docker exec redis-node1 redis-cli SAVE
# 然后复制 E:\docker-data\redis\node1\dump.rdb
```

---

## 九、常用操作命令

### 1. 查看容器状态

```bash
# 查看所有容器状态
docker-compose ps

# 查看容器日志
docker-compose logs -f mysql-master
docker-compose logs -f redis-node1
docker-compose logs -f nacos

# 查看所有服务日志
docker-compose logs -f
```

---

### 2. 进入容器

```bash
# 进入MySQL主库
docker exec -it mysql-master bash
docker exec -it mysql-master mysql -uroot -p123456

# 进入Redis
docker exec -it redis-node1 redis-cli

# 进入Elasticsearch
docker exec -it es-node1 bash

# 进入RabbitMQ
docker exec -it rabbitmq-node1 bash

# 进入Nacos
docker exec -it nacos bash
```

---

### 3. 重启服务

```bash
# 重启单个服务
docker-compose restart mysql-master

# 重启所有服务
docker-compose restart
```

---

### 4. 查看网络

```bash
# 查看Docker网络
docker network ls

# 查看网络详情
docker network inspect docker-cluster-network
```

---

## 十、健康检查

### 1. MySQL健康检查

```bash
# 检查MySQL主库
docker exec mysql-master mysqladmin -uroot -p123456 ping

# 检查MySQL从库
docker exec mysql-slave mysqladmin -uroot -p123456 ping
```

---

### 2. Redis健康检查

```bash
# 检查Redis
docker exec redis-node1 redis-cli ping
# 应该返回: PONG
```

---

### 3. Elasticsearch健康检查

```bash
# 检查ES集群健康
curl http://localhost:9200/_cluster/health?pretty
```

---

### 4. RabbitMQ健康检查

```bash
# 检查RabbitMQ
curl -u admin:123456 http://localhost:15672/api/overview
```

---

### 5. Nacos健康检查

```bash
# 检查Nacos
curl http://localhost:8848/nacos/v1/console/health/liveness
```

---

## 十一、故障排查

### 1. 容器无法启动

```bash
# 查看详细日志
docker-compose logs [service-name]

# 检查端口占用
netstat -ano | findstr :3307
netstat -ano | findstr :6379

# 检查数据目录权限
# Windows下确保目录存在且可访问
```

---

### 2. 连接失败

**检查项**:
1. 容器是否正常运行: `docker-compose ps`
2. 端口是否正确映射
3. 防火墙是否阻止连接
4. 网络配置是否正确

**测试连接**:
```bash
# 测试MySQL连接
telnet localhost 3307

# 测试Redis连接
telnet localhost 6379

# 测试HTTP连接
curl http://localhost:9200
```

---

### 3. 数据丢失

**检查数据卷**:
```bash
# 查看数据卷
docker volume ls

# 检查数据目录
# Windows: E:\docker-data\mysql\master
```

---

## 十二、配置示例汇总

### 完整的 application.yml 配置示例

```yaml
server:
  port: 8080

spring:
  application:
    name: demo
  
  # MySQL主库配置
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3307/test_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: 123456
    hikari:
      minimum-idle: 5
      maximum-pool-size: 20
  
  # Redis配置
  redis:
    host: localhost
    port: 6379
    database: 0
    timeout: 3000
    lettuce:
      pool:
        max-active: 8
        max-idle: 8
        min-idle: 0
  
  # RabbitMQ配置
  rabbitmq:
    host: localhost
    port: 5672
    username: admin
    password: 123456
    virtual-host: /
  
  # Elasticsearch配置
  elasticsearch:
    uris: http://localhost:9200
    connection-timeout: 1s
    socket-timeout: 30s
  
  # Nacos配置
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
        namespace: public
        group: DEFAULT_GROUP
      config:
        server-addr: localhost:8848
        file-extension: yaml
        namespace: public
        group: DEFAULT_GROUP

# MyBatis配置
mybatis:
  config-location: classpath:mybatis/mybatis-config.xml
  mapper-locations: classpath:mybatis/mapper/**/*.xml
  type-aliases-package: com.lixiangyu.dal.entity

# 日志配置
logging:
  level:
    root: INFO
    com.lixiangyu: DEBUG
```

---

## 十三、总结

### 连接地址速查表

| 服务 | 容器名 | 宿主机地址 | 容器内地址 | 端口映射 | 用户名/密码 |
|------|-------|-----------|-----------|---------|------------|
| MySQL主库 | mysql-master | localhost:3307 | mysql-master:3306 | 3307→3306 | root/123456 |
| MySQL从库 | mysql-slave | localhost:3308 | mysql-slave:3306 | 3308→3306 | root/123456 |
| Redis节点1 | redis-node1 | localhost:6379 | redis-node1:6379 | 6379→6379 | 无 |
| Redis节点2 | redis-node2 | localhost:6380 | redis-node2:6379 | 6380→6379 | 无 |
| Redis节点3 | redis-node3 | localhost:6381 | redis-node3:6379 | 6381→6379 | 无 |
| ES节点1 | es-node1 | localhost:9200 | es-node1:9200 | 9200→9200 | 无 |
| ES节点2 | es-node2 | localhost:9201 | es-node2:9200 | 9201→9200 | 无 |
| RabbitMQ节点1 | rabbitmq-node1 | localhost:5672 | rabbitmq-node1:5672 | 5672→5672 | admin/123456 |
| RabbitMQ节点2 | rabbitmq-node2 | localhost:5673 | rabbitmq-node2:5672 | 5673→5672 | admin/123456 |
| Nacos | nacos | localhost:8848 | nacos:8848 | 8848→8848 | nacos/nacos |

---

### 重要提示

1. **数据持久化**: 所有数据都保存在 `E:\docker-data\` 目录下，请确保该目录存在
2. **端口冲突**: 如果端口被占用，请修改 `docker-compose.yml` 中的端口映射
3. **容器间通信**: 容器间使用容器名称通信，宿主机使用 `localhost` 通信
4. **首次启动**: Nacos首次启动需要等待MySQL主库完全启动，可能需要30秒左右
5. **密码安全**: 生产环境请修改所有默认密码

---

## 十四、参考资源

- [Docker Compose官方文档](https://docs.docker.com/compose/)
- [MySQL官方文档](https://dev.mysql.com/doc/)
- [Redis官方文档](https://redis.io/documentation)
- [Elasticsearch官方文档](https://www.elastic.co/guide/en/elasticsearch/reference/current/index.html)
- [RabbitMQ官方文档](https://www.rabbitmq.com/documentation.html)
- [Nacos官方文档](https://nacos.io/docs/latest/what-is-nacos/)

