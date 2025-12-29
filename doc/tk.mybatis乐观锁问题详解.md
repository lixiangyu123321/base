# tk.mybatis 乐观锁问题详解

## 文档信息

- **创建日期**: 2025-12-29
- **问题类型**: tk.mybatis 乐观锁机制导致的 SQL 生成问题
- **影响范围**: 使用 tk.mybatis 通用 Mapper 的查询和更新操作

---

## 什么是乐观锁？

### 乐观锁的概念

**乐观锁（Optimistic Locking）**是一种并发控制机制，它假设多个事务之间很少发生冲突，因此在读取数据时不会加锁，而是在更新数据时检查数据是否被其他事务修改过。

### 乐观锁的实现方式

常见的乐观锁实现方式：

1. **版本号机制（Version）**：在表中添加一个 `version` 字段，每次更新时版本号加 1
2. **时间戳机制（Timestamp）**：使用 `update_time` 字段记录最后更新时间
3. **字段对比机制**：比较所有字段的值，确保数据未被修改

### 乐观锁的工作流程

```
1. 读取数据（带版本号）
   ↓
2. 修改数据
   ↓
3. 更新数据时检查版本号
   ↓
4. 如果版本号匹配，更新成功，版本号 +1
   ↓
5. 如果版本号不匹配，更新失败（数据已被其他事务修改）
```

---

## tk.mybatis 的乐观锁实现

### tk.mybatis 如何检测乐观锁

tk.mybatis 通过以下方式检测是否需要使用乐观锁：

1. **检查实体类是否有 `version` 字段**
   - 如果实体类中有 `version` 字段（类型为 `Integer` 或 `Long`），tk.mybatis 会自动启用乐观锁

2. **检查是否有 `@Version` 注解**
   - 如果字段上有 `@Version` 注解，也会启用乐观锁

### tk.mybatis 乐观锁的 WHERE 条件生成规则

当启用乐观锁时，tk.mybatis 会在 WHERE 条件中包含**所有非空字段**，以确保数据未被修改：

```sql
-- 正常更新（不使用乐观锁）
UPDATE table_name SET field1 = ?, field2 = ? WHERE id = ?

-- 乐观锁更新（包含所有字段的 WHERE 条件）
UPDATE table_name 
SET field1 = ?, field2 = ? 
WHERE id = ? 
  AND field1 = ?  -- 原始值
  AND field2 = ?  -- 原始值
  AND version = ? -- 原始版本号
```

### 为什么使用所有字段？

使用所有字段作为 WHERE 条件的原因：

1. **防止并发修改**：确保更新时数据未被其他事务修改
2. **数据一致性**：如果任何字段被修改，更新会失败
3. **简单实现**：不需要额外的版本号字段

---

## 问题分析

### 问题现象

在使用 `selectByPrimaryKey` 和 `updateByPrimaryKeySelective` 时，出现了以下问题：

#### 1. selectByPrimaryKey 的问题

**期望的 SQL**：
```sql
SELECT * FROM scheduler_job_config WHERE id = ?
```

**实际生成的 SQL**：
```sql
SELECT * FROM scheduler_job_config 
WHERE id = ? 
  AND job_name = ? 
  AND job_group = ? 
  AND job_class = ? 
  AND cron_expression = ? 
  AND description = ? 
  AND environment = ? 
  AND retry_count = ? 
  AND retry_interval = ? 
  AND timeout = ? 
  AND alert_enabled = ? 
  AND gray_release_enabled = ? 
  AND gray_release_percent = ? 
  AND version = ? 
  AND creator = ? 
  AND modifier = ? 
  AND create_time = ? 
  AND update_time = ?
```

**参数绑定**：
```
Parameters: 1(Long), 1(Long), 1(Long), ..., 1(Long)
```

**问题**：
- WHERE 条件包含了所有字段，但只传入了一个 ID 参数
- 所有参数都被错误地绑定为 `1(Long)`
- 查询结果为空（`Total: 0`）

#### 2. updateByPrimaryKeySelective 的问题

**期望的 SQL**：
```sql
UPDATE scheduler_job_config 
SET job_name = ?, cron_expression = ?, update_time = ? 
WHERE id = ?
```

**实际生成的 SQL**：
```sql
UPDATE scheduler_job_config 
SET id = ?, job_name = ?, job_group = ?, ..., update_time = ? 
WHERE id = ? 
  AND job_name = ? 
  AND job_group = ? 
  AND job_class = ? 
  AND cron_expression = ? 
  AND description = ? 
  AND environment = ? 
  AND retry_count = ? 
  AND retry_interval = ? 
  AND timeout = ? 
  AND alert_enabled = ? 
  AND gray_release_enabled = ? 
  AND gray_release_percent = ? 
  AND version = ? 
  AND creator = ? 
  AND modifier = ? 
  AND create_time = ? 
  AND update_time = ?
```

**参数绑定**：
```
Parameters: 1(Long), exampleJob(String), ..., 2025-12-29 21:44:09.504(Timestamp),
           1(Long), exampleJob(String), ..., 2025-12-29 21:44:09.504(Timestamp)
```

**问题**：
- WHERE 条件包含了所有字段，但传入的参数不完整
- 更新失败（`Updates: 0`）

### 问题根源

#### 1. JobConfig 实体类有 `version` 字段

```java
@Table(name = "scheduler_job_config")
public class JobConfig {
    private Long id;
    private String jobName;
    // ... 其他字段
    private Integer version;  // ← 这个字段触发了乐观锁
    // ...
}
```

#### 2. tk.mybatis 自动启用乐观锁

当检测到 `version` 字段时，tk.mybatis 会：
- 自动在 WHERE 条件中包含所有非空字段
- 期望在更新时传入所有字段的原始值

#### 3. 参数绑定错误

**selectByPrimaryKey 的参数绑定问题**：

```java
// 调用方式
JobConfig config = jobConfigMapper.selectByPrimaryKey(1L);
```

**tk.mybatis 的处理逻辑**：
1. 检测到有 `version` 字段，启用乐观锁
2. 生成包含所有字段的 WHERE 条件
3. 但只传入了一个 `Long` 类型的参数
4. MyBatis 尝试将单个参数绑定到多个占位符
5. 由于参数类型不匹配，所有参数都被错误地绑定为 `1(Long)`

**updateByPrimaryKeySelective 的参数绑定问题**：

```java
// 调用方式
JobConfig config = new JobConfig();
config.setId(1L);
config.setCronExpression("0 0/6 * * * ?");
config.setUpdateTime(new Date());
jobConfigMapper.updateByPrimaryKeySelective(config);
```

**tk.mybatis 的处理逻辑**：
1. 检测到有 `version` 字段，启用乐观锁
2. 生成包含所有字段的 WHERE 条件
3. 期望传入所有字段的原始值
4. 但 `config` 对象中只有部分字段有值
5. 未设置的字段在 WHERE 条件中无法匹配，导致更新失败

---

## 为什么会出现这个问题？

### 1. tk.mybatis 的设计理念

tk.mybatis 的设计理念是：
- **简化开发**：通过通用 Mapper 减少重复代码
- **自动处理**：自动检测并处理常见场景（如乐观锁）
- **约定优于配置**：通过约定（如 `version` 字段）自动启用功能

### 2. 乐观锁的适用场景

乐观锁适用于：
- **读多写少**的场景
- **并发冲突较少**的场景
- **需要保证数据一致性**的场景

### 3. 我们的使用场景

在我们的场景中：
- **查询操作**：只需要根据主键查询，不需要乐观锁
- **更新操作**：只需要根据主键更新，不需要检查所有字段
- **配置更新**：从 Nacos 配置中心更新，不涉及并发冲突

### 4. 冲突的原因

- **tk.mybatis 自动启用了乐观锁**（因为检测到 `version` 字段）
- **我们的使用方式不符合乐观锁的预期**（只传入主键，不传入所有字段的原始值）
- **导致 SQL 生成错误，参数绑定失败**

---

## 解决方案

### 方案一：移除 version 字段（不推荐）

**优点**：
- 简单直接
- 不会触发乐观锁

**缺点**：
- 失去了版本控制功能
- 无法防止并发修改
- 如果将来需要乐观锁，需要重新添加

### 方案二：使用自定义 SQL（推荐）

**优点**：
- 保留 `version` 字段，可以手动控制乐观锁
- 查询和更新操作更灵活
- 符合我们的使用场景

**缺点**：
- 需要编写自定义 SQL
- 需要维护额外的代码

### 方案三：禁用 tk.mybatis 的乐观锁（不推荐）

**优点**：
- 不需要修改实体类
- 不需要编写自定义 SQL

**缺点**：
- tk.mybatis 可能不支持全局禁用乐观锁
- 配置复杂，可能影响其他功能

---

## 我们的解决方案详解

### 1. 创建自定义的 selectById 方法

**Mapper 接口**：
```java
/**
 * 根据主键ID查询（不使用乐观锁）
 *
 * @param id 主键ID
 * @return 任务配置
 */
JobConfig selectById(@Param("id") Long id);
```

**Mapper XML**：
```xml
<!-- 根据主键ID查询（不使用乐观锁） -->
<select id="selectById" resultMap="BaseResultMap">
    SELECT * FROM scheduler_job_config
    WHERE id = #{id}
</select>
```

**为什么这样解决？**
- 明确指定只根据主键查询
- 不依赖 tk.mybatis 的自动检测
- WHERE 条件简单明确，参数绑定正确

### 2. 创建自定义的 updateByIdSelective 方法

**Mapper 接口**：
```java
/**
 * 根据主键ID更新（不使用乐观锁，只更新非空字段）
 *
 * @param config 任务配置
 * @return 更新的记录数
 */
int updateByIdSelective(JobConfig config);
```

**Mapper XML**：
```xml
<!-- 根据主键ID更新（不使用乐观锁，只更新非空字段） -->
<update id="updateByIdSelective">
    UPDATE scheduler_job_config
    <set>
        <if test="jobName != null">job_name = #{jobName},</if>
        <if test="jobGroup != null">job_group = #{jobGroup},</if>
        <!-- ... 其他字段 ... -->
        <if test="updateTime != null">update_time = #{updateTime},</if>
    </set>
    WHERE id = #{id}
</update>
```

**为什么这样解决？**
- 明确指定只根据主键更新
- 使用动态 SQL，只更新非空字段
- WHERE 条件简单明确，参数绑定正确
- 保留了 `version` 字段，可以手动控制版本号

### 3. 手动控制版本号

在 `update()` 方法中手动控制版本号：

```java
@Override
@Transactional(rollbackFor = Exception.class)
public JobConfig update(JobConfig config) {
    config.setUpdateTime(new Date());
    if (config.getVersion() != null) {
        // 如果传入了版本号，自动加 1
        config.setVersion(config.getVersion() + 1);
    } else {
        // 如果没有传入版本号，从数据库查询并加 1
        JobConfig existing = jobConfigMapper.selectById(config.getId());
        if (existing != null) {
            config.setVersion(existing.getVersion() + 1);
        }
    }
    int updated = jobConfigMapper.updateByIdSelective(config);
    return config;
}
```

**为什么这样解决？**
- 保留了版本号的功能
- 可以手动控制版本号的更新逻辑
- 如果需要，可以实现简单的乐观锁检查

---

## 对比分析

### 使用 tk.mybatis 的 selectByPrimaryKey

**优点**：
- 代码简洁，一行代码完成查询
- 自动处理，无需编写 SQL

**缺点**：
- 自动启用乐观锁，WHERE 条件复杂
- 参数绑定容易出错
- 查询性能可能受影响

### 使用自定义的 selectById

**优点**：
- WHERE 条件简单，查询性能好
- 参数绑定明确，不会出错
- 符合我们的使用场景

**缺点**：
- 需要编写自定义 SQL
- 需要维护额外的代码

### 使用 tk.mybatis 的 updateByPrimaryKeySelective

**优点**：
- 代码简洁，一行代码完成更新
- 自动处理，无需编写 SQL

**缺点**：
- 自动启用乐观锁，WHERE 条件复杂
- 需要传入所有字段的原始值
- 更新失败率高

### 使用自定义的 updateByIdSelective

**优点**：
- WHERE 条件简单，更新成功率高
- 只更新非空字段，灵活
- 可以手动控制版本号

**缺点**：
- 需要编写自定义 SQL
- 需要维护额外的代码

---

## 最佳实践建议

### 1. 何时使用乐观锁？

**适合使用乐观锁的场景**：
- 读多写少的场景
- 并发冲突较少的场景
- 需要保证数据一致性的场景
- 高并发场景（避免悲观锁的性能问题）

**不适合使用乐观锁的场景**：
- 写多读少的场景
- 并发冲突频繁的场景
- 简单的 CRUD 操作
- 配置更新场景（如我们的任务配置更新）

### 2. 如何正确使用 tk.mybatis 的乐观锁？

如果确实需要使用乐观锁，应该：

1. **在查询时获取完整对象**：
   ```java
   JobConfig config = jobConfigMapper.selectByPrimaryKey(id);
   // 此时 config 包含所有字段的原始值
   ```

2. **修改需要更新的字段**：
   ```java
   config.setCronExpression("0 0/6 * * * ?");
   config.setUpdateTime(new Date());
   ```

3. **更新时传入完整对象**：
   ```java
   jobConfigMapper.updateByPrimaryKeySelective(config);
   // config 包含所有字段的原始值，WHERE 条件可以正确匹配
   ```

### 3. 如何避免乐观锁问题？

**方法一：不使用 version 字段**
- 如果不需要乐观锁，不要添加 `version` 字段
- 或者使用其他名称（如 `revision`、`updateVersion`）

**方法二：使用自定义 SQL**
- 对于简单的查询和更新，使用自定义 SQL
- 避免依赖 tk.mybatis 的自动检测

**方法三：明确控制乐观锁**
- 如果需要乐观锁，明确控制版本号的更新逻辑
- 在更新前检查版本号，确保数据未被修改

---

## 总结

### 问题本质

tk.mybatis 的乐观锁机制通过检测 `version` 字段自动启用，导致：
1. WHERE 条件包含所有字段
2. 参数绑定要求传入所有字段的原始值
3. 我们的使用方式不符合乐观锁的预期
4. 导致 SQL 生成错误，查询和更新失败

### 解决方案

创建自定义的 `selectById` 和 `updateByIdSelective` 方法：
1. 明确指定只根据主键操作
2. WHERE 条件简单明确
3. 参数绑定正确
4. 保留版本号字段，可以手动控制

### 经验教训

1. **理解框架的设计理念**：tk.mybatis 通过约定自动启用功能，需要理解其工作原理
2. **选择合适的方案**：根据实际场景选择合适的方案，不要盲目使用框架的自动功能
3. **自定义优于自动**：对于简单的操作，自定义 SQL 可能更清晰、更可控
4. **保留灵活性**：保留版本号字段，但手动控制其更新逻辑，而不是依赖框架的自动处理

---

**文档版本**: 1.0  
**最后更新**: 2025-12-29

