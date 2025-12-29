# 任务调度框架 Nacos 集成说明

## 文档信息

- **创建日期**: 2025-01-27
- **功能模块**: `com.lixiangyu.common.scheduler`
- **版本**: 1.1

---

## 一、功能概述

### 1.1 新增功能

1. ✅ **配置发布到 Nacos**：自动注册时将任务配置发布到 Nacos 配置中心
2. ✅ **配置变更监听**：监听 Nacos 配置变更，实时更新数据库
3. ✅ **执行记录查询**：提供 RESTful API 查询任务执行记录
4. ✅ **立即执行任务**：提供接口立即执行任务

### 1.2 工作流程

```
任务自动注册
    ↓
保存配置到数据库
    ↓
发布配置到 Nacos
    ↓
注册配置变更监听器
    ↓
注册到调度器（如果 autoStart=true）
```

```
Nacos 配置变更
    ↓
配置变更监听器触发
    ↓
解析配置并更新数据库
    ↓
更新调度器（如果状态变更）
```

---

## 二、Nacos 配置中心集成

### 2.1 配置发布

**自动发布时机**：
- 任务自动注册时
- 配置更新时

**Data ID 格式**：
```
scheduler.job.{jobName}.{jobGroup}.{environment}.json
```

**示例**：
```
scheduler.job.exampleJob.EXAMPLE.dev.json
```

**配置内容（JSON 格式）**：
```json
{
  "jobName": "exampleJob",
  "jobGroup": "EXAMPLE",
  "jobType": "QUARTZ",
  "jobClass": "com.lixiangyu.scheduler.example.ExampleJob",
  "cronExpression": "0 0/5 * * * ?",
  "description": "示例任务",
  "status": "RUNNING",
  "environment": "dev",
  "retryCount": 3,
  "retryInterval": 60,
  "timeout": 300,
  "alertEnabled": true,
  "alertTypes": ["DINGTALK"],
  "alertReceivers": {
    "dingtalk": ["13800138000"]
  },
  "grayReleaseEnabled": false,
  "grayReleasePercent": 0,
  "version": 1
}
```

### 2.2 配置变更监听

**监听机制**：
- 使用 `DynamicConfigManager` 监听 Nacos 配置变更
- 每个任务注册独立的配置监听器
- 配置变更时自动更新数据库

**更新内容**：
- Cron 表达式
- 任务参数
- 任务描述
- 任务状态
- 重试配置
- 超时时间
- 告警配置

**调度器同步**：
- 如果状态变更为 `RUNNING`，自动启动任务
- 如果状态变更为 `STOPPED`，自动停止任务
- 如果状态变更为 `PAUSED`，自动暂停任务

---

## 三、RESTful API

### 3.1 任务配置管理

#### 查询任务配置列表
```http
GET /api/scheduler/job/config/list?environment=dev&status=RUNNING
```

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "jobName": "exampleJob",
      "jobGroup": "EXAMPLE",
      "jobType": "QUARTZ",
      "cronExpression": "0 0/5 * * * ?",
      "status": "RUNNING"
    }
  ]
}
```

#### 查询任务配置详情
```http
GET /api/scheduler/job/config/{id}
```

#### 创建任务配置
```http
POST /api/scheduler/job/config
Content-Type: application/json

{
  "jobName": "newJob",
  "jobGroup": "NEW",
  "jobType": "QUARTZ",
  "jobClass": "com.lixiangyu.job.NewJob",
  "cronExpression": "0 0/10 * * * ?",
  "description": "新任务"
}
```

#### 更新任务配置
```http
PUT /api/scheduler/job/config/{id}
Content-Type: application/json

{
  "cronExpression": "0 0/15 * * * ?",
  "description": "更新后的描述"
}
```

#### 删除任务配置
```http
DELETE /api/scheduler/job/config/{id}
```

### 3.2 任务控制

#### 启动任务
```http
POST /api/scheduler/job/{id}/start
```

#### 停止任务
```http
POST /api/scheduler/job/{id}/stop
```

#### 暂停任务
```http
POST /api/scheduler/job/{id}/pause
```

#### 恢复任务
```http
POST /api/scheduler/job/{id}/resume
```

### 3.3 任务执行

#### 立即执行任务
```http
POST /api/scheduler/job/{id}/execute
```

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "success": true,
    "errorMessage": null,
    "jobId": 1,
    "jobName": "exampleJob"
  }
}
```

### 3.4 执行记录查询

#### 查询任务执行记录列表
```http
GET /api/scheduler/job/{id}/logs?limit=50
```

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 100,
      "jobId": 1,
      "jobName": "exampleJob",
      "executionId": "uuid-123",
      "startTime": "2025-01-27 10:00:00",
      "endTime": "2025-01-27 10:00:05",
      "duration": 5000,
      "status": "SUCCESS",
      "retryCount": 0
    }
  ]
}
```

#### 查询执行记录详情
```http
GET /api/scheduler/job/log/{logId}
```

#### 根据执行ID查询
```http
GET /api/scheduler/job/log/execution/{executionId}
```

### 3.5 任务统计

#### 查询任务统计信息
```http
GET /api/scheduler/job/{id}/statistics
```

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "jobId": 1,
    "jobName": "exampleJob",
    "totalCount": 100,
    "successCount": 95,
    "failedCount": 5,
    "successRate": 95.0,
    "avgDuration": 5234.5,
    "status": "RUNNING"
  }
}
```

---

## 四、使用示例

### 4.1 自动注册并发布到 Nacos

```java
@Component
@ScheduledJob(
        jobName = "myJob",
        jobGroup = "MY_GROUP",
        cronExpression = "0 0/10 * * * ?",
        autoStart = true,
        loadFromDatabase = true
)
public class MyJob implements Job {
    
    @Override
    public void execute(JobContext context) throws Exception {
        context.log("执行任务");
    }
}
```

**流程**：
1. 应用启动时自动扫描 `MyJob`
2. 检查数据库是否有配置
3. 如果没有，使用注解配置创建并保存到数据库
4. 发布配置到 Nacos（Data ID: `scheduler.job.myJob.MY_GROUP.dev.json`）
5. 注册配置变更监听器
6. 注册到调度器并启动

### 4.2 通过 Nacos 修改配置

**步骤 1：在 Nacos 控制台修改配置**

Data ID: `scheduler.job.myJob.MY_GROUP.dev.json`

```json
{
  "jobName": "myJob",
  "jobGroup": "MY_GROUP",
  "cronExpression": "0 0/15 * * * ?",  // 修改为每15分钟
  "status": "RUNNING",
  ...
}
```

**步骤 2：配置自动生效**

1. Nacos 推送配置变更
2. 配置变更监听器触发
3. 更新数据库配置
4. 如果 Cron 表达式变更，更新调度器
5. 如果状态变更，更新调度器状态

### 4.3 立即执行任务

```bash
# 通过 API 立即执行任务
curl -X POST http://localhost:8080/api/scheduler/job/1/execute
```

**响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "success": true,
    "jobId": 1,
    "jobName": "myJob"
  }
}
```

### 4.4 查询执行记录

```bash
# 查询最近的执行记录
curl http://localhost:8080/api/scheduler/job/1/logs?limit=10

# 查询执行记录详情
curl http://localhost:8080/api/scheduler/job/log/100

# 查询任务统计
curl http://localhost:8080/api/scheduler/job/1/statistics
```

---

## 五、配置优先级

### 5.1 配置来源优先级

1. **Nacos 配置中心**（最高优先级）
   - 配置变更时自动更新数据库
   - 实时生效，无需重启

2. **数据库配置**
   - 持久化存储
   - 应用启动时加载

3. **注解配置**
   - 代码中的默认配置
   - 仅在数据库中没有配置时使用

### 5.2 配置同步流程

```
Nacos 配置变更
    ↓
配置变更监听器
    ↓
更新数据库
    ↓
更新调度器
    ↓
配置生效
```

---

## 六、最佳实践

### 6.1 配置管理

1. **开发环境**：使用注解配置，快速开发
2. **测试环境**：使用数据库配置，便于测试
3. **生产环境**：使用 Nacos 配置，支持动态调整

### 6.2 配置变更

1. **Cron 表达式变更**：先在测试环境验证，再同步到生产
2. **任务参数变更**：通过 Nacos 修改，实时生效
3. **任务状态变更**：通过 Nacos 或 API 修改

### 6.3 监控告警

1. **执行记录查询**：定期查询任务执行记录
2. **统计信息**：监控任务成功率、平均执行时长
3. **失败告警**：配置告警规则，及时发现问题

---

## 七、注意事项

### 7.1 Nacos 配置

1. ✅ Data ID 格式必须正确
2. ✅ 配置内容必须是有效的 JSON
3. ✅ 配置变更后会自动更新数据库

### 7.2 配置同步

1. ✅ 数据库是配置的持久化存储
2. ✅ Nacos 配置变更会同步到数据库
3. ✅ 调度器状态会随配置变更自动更新

### 7.3 立即执行

1. ✅ 立即执行不会影响定时调度
2. ✅ 立即执行会记录执行日志
3. ✅ 立即执行支持失败重试

---

**Nacos 集成功能已完整实现！** 🎉

