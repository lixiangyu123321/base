package com.lixiangyu.common.migration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 双写切面
 * 拦截带有 @DualWrite 注解的方法，实现双写功能
 * 
 * 功能特性：
 * 1. 自动拦截数据库写入操作
 * 2. 同时写入源库和目标库
 * 3. 支持同步和异步写入
 * 4. 支持失败回滚
 * 5. 支持重试机制
 *
 * TODO 改写双写
 * TODO 动态切换数据源，支持多数据源
 * 12-31 19:00 这里的双写其实蛮鸡肋的，其实我希望的是扩展MyBatis的相关的接口，再MyBatis的基础上实现双写
 *             看看MyBatis的扩展可不可以实现双写，或者这里的双写改为动态切换数据源，或者支持多数据源
 * @author lixiangyu
 */
@Slf4j
@Aspect
@Component
@Order(1) // 确保在事务切面之前执行
@RequiredArgsConstructor
public class DualWriteAspect {
    
    @Autowired
    private ApplicationContext applicationContext;
    
    @Autowired
    private DualWriteConfigManager configManager;
    
    /**
     * 异步执行器
     */
    private final ExecutorService asyncExecutor = Executors.newFixedThreadPool(10);
    
    /**
     * 环绕通知：执行双写
     */
    @Around("@annotation(dualWrite)")
    public Object executeDualWrite(ProceedingJoinPoint joinPoint, DualWrite dualWrite) throws Throwable {
        log.debug("检测到 @DualWrite 注解，开始执行双写");
        
        // 1. 检查双写开关（从配置中心）
        String[] tables = dualWrite.tables();
        if (tables.length > 0) {
            // 检查表级别的双写开关
            boolean shouldDualWrite = false;
            for (String table : tables) {
                if (configManager.shouldDualWrite(table)) {
                    shouldDualWrite = true;
                    break;
                }
            }
            
            if (!shouldDualWrite) {
                log.debug("表 {} 的双写功能已关闭，跳过双写", java.util.Arrays.toString(tables));
                return joinPoint.proceed();
            }
        } else {
            // 检查全局双写开关
            if (!configManager.shouldDualWrite(null)) {
                log.debug("全局双写功能已关闭，跳过双写");
                return joinPoint.proceed();
            }
        }
        
        // 2. 获取数据源
        DataSource sourceDataSource = getDataSource(dualWrite.source(), "source");
        DataSource targetDataSource = getDataSource(dualWrite.target(), "target");
        
        if (sourceDataSource == null || targetDataSource == null) {
            log.warn("数据源未配置，跳过双写，执行原方法");
            return joinPoint.proceed();
        }
        
        // 获取方法参数
        Object[] args = joinPoint.getArgs();
        Method method = getMethod(joinPoint);
        
        try {
            // 根据写入顺序执行
            switch (dualWrite.order()) {
                case SOURCE_FIRST:
                    return executeSourceFirst(joinPoint, dualWrite, sourceDataSource, targetDataSource, method, args);
                case TARGET_FIRST:
                    return executeTargetFirst(joinPoint, dualWrite, sourceDataSource, targetDataSource, method, args);
                case PARALLEL:
                    return executeParallel(joinPoint, dualWrite, sourceDataSource, targetDataSource, method, args);
                default:
                    return joinPoint.proceed();
            }
        } catch (Exception e) {
            log.error("双写执行失败", e);
            if (dualWrite.rollbackOnFailure()) {
                throw e;
            }
            // 不回滚，只记录错误
            return joinPoint.proceed();
        }
    }
    
    /**
     * 先写源库，再写目标库
     */
    private Object executeSourceFirst(
            ProceedingJoinPoint joinPoint,
            DualWrite dualWrite,
            DataSource sourceDataSource,
            DataSource targetDataSource,
            Method method,
            Object[] args) throws Throwable {
        
        // 1. 检查是否应该写源库
        String[] tables = dualWrite.tables();
        boolean shouldWriteSource = true;
        if (tables.length > 0) {
            for (String table : tables) {
                if (!configManager.shouldWriteSource(table)) {
                    shouldWriteSource = false;
                    break;
                }
            }
        } else {
            shouldWriteSource = configManager.shouldWriteSource(null);
        }
        
        // 2. 执行原方法（写源库，如果开关开启）
        final Object[] resultHolder = new Object[1];
        if (shouldWriteSource) {
            resultHolder[0] = joinPoint.proceed();
        } else {
            log.debug("写源库开关已关闭，跳过源库写入");
            // 如果源库不写，直接返回 null 或默认值
            if (method.getReturnType() != void.class) {
                resultHolder[0] = getDefaultReturnValue(method.getReturnType());
            }
        }
        
        // 3. 检查是否应该写目标库
        boolean shouldWriteTarget = true;
        if (tables.length > 0) {
            for (String table : tables) {
                if (!configManager.shouldWriteTarget(table)) {
                    shouldWriteTarget = false;
                    break;
                }
            }
        } else {
            shouldWriteTarget = configManager.shouldWriteTarget(null);
        }
        
        // 4. 写目标库（如果开关开启）
        if (shouldWriteTarget) {
            if (dualWrite.async()) {
                // 异步写入
                CompletableFuture.runAsync(() -> {
                    writeToTarget(dualWrite, targetDataSource, method, args, resultHolder[0]);
                }, asyncExecutor);
            } else {
                // 同步写入
                writeToTargetWithRetry(dualWrite, targetDataSource, method, args, resultHolder[0]);
            }
        } else {
            log.debug("写目标库开关已关闭，跳过目标库写入");
        }
        
        return resultHolder[0];
    }
    
    /**
     * 获取默认返回值
     */
    private Object getDefaultReturnValue(Class<?> returnType) {
        if (returnType == void.class || returnType == Void.class) {
            return null;
        }
        if (returnType.isPrimitive()) {
            if (returnType == boolean.class) return false;
            if (returnType == byte.class) return (byte) 0;
            if (returnType == short.class) return (short) 0;
            if (returnType == int.class) return 0;
            if (returnType == long.class) return 0L;
            if (returnType == float.class) return 0.0f;
            if (returnType == double.class) return 0.0;
            if (returnType == char.class) return '\u0000';
        }
        return null;
    }
    
    /**
     * 先写目标库，再写源库
     */
    private Object executeTargetFirst(
            ProceedingJoinPoint joinPoint,
            DualWrite dualWrite,
            DataSource sourceDataSource,
            DataSource targetDataSource,
            Method method,
            Object[] args) throws Throwable {
        
        // 1. 检查是否应该写目标库
        String[] tables = dualWrite.tables();
        boolean shouldWriteTarget = true;
        if (tables.length > 0) {
            for (String table : tables) {
                if (!configManager.shouldWriteTarget(table)) {
                    shouldWriteTarget = false;
                    break;
                }
            }
        } else {
            shouldWriteTarget = configManager.shouldWriteTarget(null);
        }
        
        // 2. 先写目标库（如果开关开启）
        if (shouldWriteTarget) {
            writeToTargetWithRetry(dualWrite, targetDataSource, method, args, null);
        } else {
            log.debug("写目标库开关已关闭，跳过目标库写入");
        }
        
        // 3. 检查是否应该写源库
        boolean shouldWriteSource = true;
        if (tables.length > 0) {
            for (String table : tables) {
                if (!configManager.shouldWriteSource(table)) {
                    shouldWriteSource = false;
                    break;
                }
            }
        } else {
            shouldWriteSource = configManager.shouldWriteSource(null);
        }
        
        // 4. 执行原方法（写源库，如果开关开启）
        if (shouldWriteSource) {
            return joinPoint.proceed();
        } else {
            log.debug("写源库开关已关闭，跳过源库写入");
            return getDefaultReturnValue(method.getReturnType());
        }
    }
    
    /**
     * 并行写入
     */
    private Object executeParallel(
            ProceedingJoinPoint joinPoint,
            DualWrite dualWrite,
            DataSource sourceDataSource,
            DataSource targetDataSource,
            Method method,
            Object[] args) throws Throwable {
        
        // 并行执行
        CompletableFuture<Object> sourceFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return joinPoint.proceed();
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });
        
        CompletableFuture<Void> targetFuture = CompletableFuture.runAsync(() -> {
            writeToTargetWithRetry(dualWrite, targetDataSource, method, args, null);
        });
        
        // 等待两个都完成
        CompletableFuture.allOf(sourceFuture, targetFuture).join();
        
        return sourceFuture.get();
    }
    
    /**
     * 写入目标库（带重试）
     */
    private void writeToTargetWithRetry(
            DualWrite dualWrite,
            DataSource targetDataSource,
            Method method,
            Object[] args,
            Object result) {
        
        int retryTimes = dualWrite.retryTimes();
        long retryInterval = dualWrite.retryInterval();
        
        for (int i = 0; i <= retryTimes; i++) {
            try {
                writeToTarget(dualWrite, targetDataSource, method, args, result);
                return; // 成功，退出
            } catch (Exception e) {
                if (i == retryTimes) {
                    log.error("写入目标库失败，已重试 {} 次", retryTimes, e);
                    if (dualWrite.rollbackOnFailure()) {
                        throw new RuntimeException("写入目标库失败", e);
                    }
                } else {
                    log.warn("写入目标库失败，{}ms 后重试，剩余重试次数: {}", retryInterval, retryTimes - i, e);
                    try {
                        Thread.sleep(retryInterval);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("重试被中断", ie);
                    }
                }
            }
        }
    }
    
    /**
     * 写入目标库
     */
    private void writeToTarget(
            DualWrite dualWrite,
            DataSource targetDataSource,
            Method method,
            Object[] args,
            Object result) {
        
        try {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(targetDataSource);
            
            // 解析 SQL 语句（从方法名或参数推断）
            String sql = parseSqlFromMethod(method, args, result);
            
            if (sql != null) {
                // 执行 SQL
                executeSql(jdbcTemplate, sql, args);
                log.debug("成功写入目标库: {}", sql);
            } else {
                log.warn("无法解析 SQL，跳过双写");
            }
            
        } catch (Exception e) {
            log.error("写入目标库失败", e);
            throw new RuntimeException("写入目标库失败", e);
        }
    }
    
    /**
     * 从方法解析 SQL
     * 支持 MyBatis Mapper、JPA Repository、实体类注解等多种方式
     */
    private String parseSqlFromMethod(Method method, Object[] args, Object result) {
        String methodName = method.getName().toLowerCase();
        
        // 1. 尝试从 JPA Repository 解析
        String sql = parseSqlFromJpa(method, args, result);
        if (sql != null) {
            return sql;
        }
        
        // 2. 根据方法名推断 SQL 类型
        if (methodName.contains("insert") || methodName.contains("save") || methodName.contains("add")) {
            return parseInsertSql(method, args);
        } else if (methodName.contains("update") || methodName.contains("modify")) {
            return parseUpdateSql(method, args);
        } else if (methodName.contains("delete") || methodName.contains("remove")) {
            return parseDeleteSql(method, args);
        }
        
        return null;
    }
    
    /**
     * 从 JPA Repository 解析 SQL
     */
    private String parseSqlFromJpa(Method method, Object[] args, Object result) {
        try {
            // 检查是否是 JPA Repository 方法
            Class<?> declaringClass = method.getDeclaringClass();
            
            // 检查是否实现了 JpaRepository 或类似接口
            boolean isJpaRepository = false;
            for (Class<?> iface : declaringClass.getInterfaces()) {
                String ifaceName = iface.getName();
                if (ifaceName.contains("Repository") || 
                    ifaceName.contains("JpaRepository") ||
                    ifaceName.contains("CrudRepository")) {
                    isJpaRepository = true;
                    break;
                }
            }
            
            if (!isJpaRepository) {
                return null;
            }
            
            // 根据 JPA 方法名推断 SQL
            String methodName = method.getName().toLowerCase();
            Object entity = args.length > 0 ? args[0] : null;
            
            if (entity == null) {
                return null;
            }
            
            // 获取实体类信息
            Class<?> entityClass = entity.getClass();
            String tableName = getTableName(entityClass);
            if (tableName == null) {
                return null;
            }
            
            // 根据方法名构建 SQL
            if (methodName.contains("save") || methodName.contains("insert")) {
                return parseInsertSqlFromEntity(entity);
            } else if (methodName.contains("update") || methodName.contains("modify")) {
                return parseUpdateSqlFromEntity(entity);
            } else if (methodName.contains("delete") || methodName.contains("remove")) {
                return parseDeleteSqlFromEntity(entity);
            }
            
        } catch (Exception e) {
            log.debug("从 JPA Repository 解析 SQL 失败", e);
        }
        
        return null;
    }
    
    /**
     * 解析 INSERT SQL
     */
    private String parseInsertSql(Method method, Object[] args) {
        // 1. 尝试从 MyBatis Mapper 解析
        String sql = parseSqlFromMyBatis(method, args, "INSERT");
        if (sql != null) {
            return sql;
        }
        
        // 2. 尝试从实体类注解解析
        if (args.length > 0 && args[0] != null) {
            sql = parseInsertSqlFromEntity(args[0]);
            if (sql != null) {
                return sql;
            }
        }
        
        // 3. 降级方案：根据方法参数推断
        return buildInsertSqlFromArgs(method, args);
    }
    
    /**
     * 解析 UPDATE SQL
     */
    private String parseUpdateSql(Method method, Object[] args) {
        // 1. 尝试从 MyBatis Mapper 解析
        String sql = parseSqlFromMyBatis(method, args, "UPDATE");
        if (sql != null) {
            return sql;
        }
        
        // 2. 尝试从实体类注解解析
        if (args.length > 0 && args[0] != null) {
            sql = parseUpdateSqlFromEntity(args[0]);
            if (sql != null) {
                return sql;
            }
        }
        
        // 3. 降级方案：根据方法参数推断
        return buildUpdateSqlFromArgs(method, args);
    }
    
    /**
     * 解析 DELETE SQL
     */
    private String parseDeleteSql(Method method, Object[] args) {
        // 1. 尝试从 MyBatis Mapper 解析
        String sql = parseSqlFromMyBatis(method, args, "DELETE");
        if (sql != null) {
            return sql;
        }
        
        // 2. 尝试从实体类注解解析
        if (args.length > 0 && args[0] != null) {
            sql = parseDeleteSqlFromEntity(args[0]);
            if (sql != null) {
                return sql;
            }
        }
        
        // 3. 降级方案：根据方法参数推断
        return buildDeleteSqlFromArgs(method, args);
    }
    
    /**
     * 从 MyBatis Mapper 解析 SQL
     */
    private String parseSqlFromMyBatis(Method method, Object[] args, String sqlType) {
        try {
            // 尝试获取 MyBatis SqlSessionFactory
            if (applicationContext != null) {
                try {
                    Class<?> sqlSessionFactoryClass = Class.forName("org.apache.ibatis.session.SqlSessionFactory");
                    Object sqlSessionFactory = applicationContext.getBean(sqlSessionFactoryClass);
                    
                    // 获取 Mapper 接口
                    Class<?> mapperClass = method.getDeclaringClass();
                    // mapper 用于后续获取 SQL，这里先获取但不使用（避免警告）
                    @SuppressWarnings("unused")
                    Object mapper = applicationContext.getBean(mapperClass);
                    
                    // 通过 MyBatis 的 Configuration 获取 SQL
                    Object configuration = sqlSessionFactoryClass.getMethod("getConfiguration").invoke(sqlSessionFactory);
                    Class<?> configurationClass = Class.forName("org.apache.ibatis.session.Configuration");
                    
                    // 获取 MappedStatement
                    String statementId = mapperClass.getName() + "." + method.getName();
                    Object mappedStatement = configurationClass.getMethod("getMappedStatement", String.class)
                            .invoke(configuration, statementId);
                    
                    if (mappedStatement != null) {
                        Class<?> mappedStatementClass = Class.forName("org.apache.ibatis.mapping.MappedStatement");
                        Object sqlSource = mappedStatementClass.getMethod("getSqlSource").invoke(mappedStatement);
                        
                        // 获取 BoundSql
                        Class<?> sqlSourceClass = Class.forName("org.apache.ibatis.mapping.SqlSource");
                        Object boundSql = sqlSourceClass.getMethod("getBoundSql", Object.class)
                                .invoke(sqlSource, args.length > 0 ? args[0] : null);
                        
                        Class<?> boundSqlClass = Class.forName("org.apache.ibatis.mapping.BoundSql");
                        String sql = (String) boundSqlClass.getMethod("getSql").invoke(boundSql);
                        
                        return sql;
                    }
                } catch (ClassNotFoundException e) {
                    // MyBatis 未配置
                } catch (Exception e) {
                    log.debug("从 MyBatis 解析 SQL 失败", e);
                }
            }
        } catch (Exception e) {
            log.debug("解析 MyBatis SQL 异常", e);
        }
        
        return null;
    }
    
    /**
     * 从实体类注解解析 INSERT SQL
     */
    private String parseInsertSqlFromEntity(Object entity) {
        try {
            Class<?> entityClass = entity.getClass();
            
            // 获取表名（@Table 注解或类名）
            String tableName = getTableName(entityClass);
            if (tableName == null) {
                return null;
            }
            
            // 获取所有字段
            List<FieldInfo> fields = getEntityFields(entityClass, entity);
            if (fields.isEmpty()) {
                return null;
            }
            
            // 构建 INSERT SQL
            StringBuilder sql = new StringBuilder("INSERT INTO ");
            sql.append(tableName).append(" (");
            
            String columns = fields.stream()
                    .map(FieldInfo::getColumnName)
                    .collect(java.util.stream.Collectors.joining(", "));
            sql.append(columns).append(") VALUES (");
            
            String placeholders = fields.stream()
                    .map(f -> "?")
                    .collect(java.util.stream.Collectors.joining(", "));
            sql.append(placeholders).append(")");
            
            return sql.toString();
        } catch (Exception e) {
            log.debug("从实体类解析 INSERT SQL 失败", e);
            return null;
        }
    }
    
    /**
     * 从实体类注解解析 UPDATE SQL
     */
    private String parseUpdateSqlFromEntity(Object entity) {
        try {
            Class<?> entityClass = entity.getClass();
            String tableName = getTableName(entityClass);
            if (tableName == null) {
                return null;
            }
            
            List<FieldInfo> fields = getEntityFields(entityClass, entity);
            List<String> primaryKeys = getPrimaryKeys(entityClass);
            
            if (primaryKeys.isEmpty()) {
                log.warn("实体类 {} 没有主键，无法构建 UPDATE SQL", entityClass.getName());
                return null;
            }
            
            // 构建 UPDATE SQL
            StringBuilder sql = new StringBuilder("UPDATE ");
            sql.append(tableName).append(" SET ");
            
            String setClause = fields.stream()
                    .filter(f -> !primaryKeys.contains(f.getColumnName()))
                    .map(f -> f.getColumnName() + " = ?")
                    .collect(java.util.stream.Collectors.joining(", "));
            sql.append(setClause).append(" WHERE ");
            
            String whereClause = primaryKeys.stream()
                    .map(key -> key + " = ?")
                    .collect(java.util.stream.Collectors.joining(" AND "));
            sql.append(whereClause);
            
            return sql.toString();
        } catch (Exception e) {
            log.debug("从实体类解析 UPDATE SQL 失败", e);
            return null;
        }
    }
    
    /**
     * 从实体类注解解析 DELETE SQL
     */
    private String parseDeleteSqlFromEntity(Object entity) {
        try {
            Class<?> entityClass = entity.getClass();
            String tableName = getTableName(entityClass);
            if (tableName == null) {
                return null;
            }
            
            List<String> primaryKeys = getPrimaryKeys(entityClass);
            if (primaryKeys.isEmpty()) {
                log.warn("实体类 {} 没有主键，无法构建 DELETE SQL", entityClass.getName());
                return null;
            }
            
            // 构建 DELETE SQL
            StringBuilder sql = new StringBuilder("DELETE FROM ");
            sql.append(tableName).append(" WHERE ");
            
            String whereClause = primaryKeys.stream()
                    .map(key -> key + " = ?")
                    .collect(java.util.stream.Collectors.joining(" AND "));
            sql.append(whereClause);
            
            return sql.toString();
        } catch (Exception e) {
            log.debug("从实体类解析 DELETE SQL 失败", e);
            return null;
        }
    }
    
    /**
     * 从参数构建 INSERT SQL（降级方案）
     */
    private String buildInsertSqlFromArgs(Method method, Object[] args) {
        // 简化实现：根据第一个参数推断表名
        if (args.length > 0 && args[0] != null) {
            String tableName = inferTableName(args[0].getClass());
            if (tableName != null) {
                // 简化：使用 REPLACE INTO（兼容 INSERT）
                return "REPLACE INTO " + tableName + " VALUES (?)";
            }
        }
        return null;
    }
    
    /**
     * 从参数构建 UPDATE SQL（降级方案）
     */
    private String buildUpdateSqlFromArgs(Method method, Object[] args) {
        if (args.length > 0 && args[0] != null) {
            String tableName = inferTableName(args[0].getClass());
            if (tableName != null) {
                return "UPDATE " + tableName + " SET ? = ? WHERE id = ?";
            }
        }
        return null;
    }
    
    /**
     * 从参数构建 DELETE SQL（降级方案）
     */
    private String buildDeleteSqlFromArgs(Method method, Object[] args) {
        if (args.length > 0) {
            // 假设第一个参数是 ID
            String tableName = inferTableName(method.getDeclaringClass());
            if (tableName != null) {
                return "DELETE FROM " + tableName + " WHERE id = ?";
            }
        }
        return null;
    }
    
    /**
     * 获取表名
     */
    private String getTableName(Class<?> entityClass) {
        // 尝试 @Table 注解
        try {
            Class<?> tableAnnotation = Class.forName("javax.persistence.Table");
            @SuppressWarnings("unchecked")
            Class<? extends java.lang.annotation.Annotation> annotationClass = 
                    (Class<? extends java.lang.annotation.Annotation>) tableAnnotation;
            java.lang.annotation.Annotation annotation = entityClass.getAnnotation(annotationClass);
            if (annotation != null) {
                String name = (String) tableAnnotation.getMethod("name").invoke(annotation);
                if (name != null && !name.isEmpty()) {
                    return name;
                }
            }
        } catch (Exception e) {
            // 忽略
        }
        
        // 降级：从类名推断
        return inferTableName(entityClass);
    }
    
    /**
     * 推断表名
     */
    private String inferTableName(Class<?> clazz) {
        String className = clazz.getSimpleName();
        // 移除 DO、Entity 等后缀
        className = className.replaceAll("(DO|Entity|Model)$", "");
        // 驼峰转下划线
        return camelToUnderscore(className);
    }
    
    /**
     * 驼峰转下划线
     */
    private String camelToUnderscore(String str) {
        return str.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
    
    /**
     * 获取实体类字段信息
     */
    private List<FieldInfo> getEntityFields(Class<?> entityClass, Object entity) {
        List<FieldInfo> fields = new java.util.ArrayList<>();
        
        // 遍历所有字段
        Class<?> currentClass = entityClass;
        while (currentClass != null && currentClass != Object.class) {
            for (java.lang.reflect.Field field : currentClass.getDeclaredFields()) {
                // 跳过静态字段和序列化字段
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) ||
                    field.getName().equals("serialVersionUID")) {
                    continue;
                }
                
                try {
                    field.setAccessible(true);
                    Object value = field.get(entity);
                    
                    // 获取列名
                    String columnName = getColumnName(field);
                    if (columnName != null) {
                        fields.add(new FieldInfo(field.getName(), columnName, value));
                    }
                } catch (Exception e) {
                    log.debug("获取字段值失败: {}", field.getName(), e);
                }
            }
            currentClass = currentClass.getSuperclass();
        }
        
        return fields;
    }
    
    /**
     * 获取字段对应的列名
     */
    private String getColumnName(java.lang.reflect.Field field) {
        // 尝试 @Column 注解
        try {
            Class<?> columnAnnotation = Class.forName("javax.persistence.Column");
            @SuppressWarnings("unchecked")
            Class<? extends java.lang.annotation.Annotation> annotationClass = 
                    (Class<? extends java.lang.annotation.Annotation>) columnAnnotation;
            java.lang.annotation.Annotation annotation = field.getAnnotation(annotationClass);
            if (annotation != null) {
                String name = (String) columnAnnotation.getMethod("name").invoke(annotation);
                if (name != null && !name.isEmpty()) {
                    return name;
                }
            }
        } catch (Exception e) {
            // 忽略
        }
        
        // 降级：使用字段名（驼峰转下划线）
        return camelToUnderscore(field.getName());
    }
    
    /**
     * 获取主键列表
     */
    private List<String> getPrimaryKeys(Class<?> entityClass) {
        List<String> primaryKeys = new java.util.ArrayList<>();
        
        // 遍历字段查找 @Id 注解
        Class<?> currentClass = entityClass;
        while (currentClass != null && currentClass != Object.class) {
            for (java.lang.reflect.Field field : currentClass.getDeclaredFields()) {
                try {
                    Class<?> idAnnotation = Class.forName("javax.persistence.Id");
                    @SuppressWarnings("unchecked")
                    Class<? extends java.lang.annotation.Annotation> annotationClass = 
                            (Class<? extends java.lang.annotation.Annotation>) idAnnotation;
                    if (field.isAnnotationPresent(annotationClass)) {
                        String columnName = getColumnName(field);
                        if (columnName != null) {
                            primaryKeys.add(columnName);
                        } else {
                            primaryKeys.add(camelToUnderscore(field.getName()));
                        }
                    }
                } catch (Exception e) {
                    // 忽略
                }
            }
            currentClass = currentClass.getSuperclass();
        }
        
        // 如果没有找到 @Id，默认使用 id 字段
        if (primaryKeys.isEmpty()) {
            primaryKeys.add("id");
        }
        
        return primaryKeys;
    }
    
    /**
     * 字段信息
     */
    private static class FieldInfo {
        private String fieldName;
        private String columnName;
        private Object value;
        
        public FieldInfo(String fieldName, String columnName, Object value) {
            this.fieldName = fieldName;
            this.columnName = columnName;
            this.value = value;
        }
        
        @SuppressWarnings("unused")
        public String getFieldName() { return fieldName; }
        public String getColumnName() { return columnName; }
        public Object getValue() { return value; }
    }
    
    /**
     * 执行 SQL
     */
    private void executeSql(JdbcTemplate jdbcTemplate, String sql, Object[] args) {
        try {
            if (sql == null || sql.trim().isEmpty()) {
                log.warn("SQL 为空，跳过执行");
                return;
            }
            
            // 根据 SQL 类型执行
            String sqlUpper = sql.trim().toUpperCase();
            if (sqlUpper.startsWith("INSERT") || sqlUpper.startsWith("REPLACE")) {
                // INSERT 操作
                if (args.length > 0 && args[0] != null) {
                    // 如果是实体对象，提取字段值
                    Object[] params = extractParamsFromEntity(args[0], sql);
                    jdbcTemplate.update(sql, params);
                } else {
                    jdbcTemplate.update(sql, args);
                }
            } else if (sqlUpper.startsWith("UPDATE")) {
                // UPDATE 操作
                if (args.length > 0 && args[0] != null) {
                    Object[] params = extractParamsFromEntity(args[0], sql);
                    jdbcTemplate.update(sql, params);
                } else {
                    jdbcTemplate.update(sql, args);
                }
            } else if (sqlUpper.startsWith("DELETE")) {
                // DELETE 操作
                jdbcTemplate.update(sql, args);
            } else {
                // 其他操作
                jdbcTemplate.update(sql, args);
            }
            
            log.debug("成功执行 SQL: {}", sql);
        } catch (Exception e) {
            log.error("执行 SQL 失败: {}", sql, e);
            throw new RuntimeException("执行 SQL 失败: " + sql, e);
        }
    }
    
    /**
     * 从实体对象提取参数
     */
    private Object[] extractParamsFromEntity(Object entity, String sql) {
        try {
            Class<?> entityClass = entity.getClass();
            List<FieldInfo> fields = getEntityFields(entityClass, entity);
            
            // 根据 SQL 中的占位符数量提取参数
            int placeholderCount = countPlaceholders(sql);
            Object[] params = new Object[placeholderCount];
            
            int index = 0;
            for (FieldInfo field : fields) {
                if (index < placeholderCount) {
                    params[index++] = field.getValue();
                }
            }
            
            return params;
        } catch (Exception e) {
            log.error("从实体对象提取参数失败", e);
            return new Object[0];
        }
    }
    
    /**
     * 统计 SQL 中的占位符数量
     */
    private int countPlaceholders(String sql) {
        int count = 0;
        for (int i = 0; i < sql.length(); i++) {
            if (sql.charAt(i) == '?') {
                count++;
            }
        }
        return count;
    }
    
    /**
     * 获取数据源
     */
    private DataSource getDataSource(String beanName, String type) {
        if (beanName == null || beanName.isEmpty()) {
            // 使用默认数据源
            try {
                return applicationContext.getBean(DataSource.class);
            } catch (Exception e) {
                log.warn("未找到默认数据源，类型: {}", type);
                return null;
            }
        }
        
        try {
            return applicationContext.getBean(beanName, DataSource.class);
        } catch (Exception e) {
            log.warn("未找到数据源 Bean: {}, 类型: {}", beanName, type);
            return null;
        }
    }
    
    /**
     * 获取方法
     */
    private Method getMethod(ProceedingJoinPoint joinPoint) throws NoSuchMethodException {
        String methodName = joinPoint.getSignature().getName();
        Class<?> targetClass = joinPoint.getTarget().getClass();
        Class<?>[] parameterTypes = new Class[joinPoint.getArgs().length];
        for (int i = 0; i < joinPoint.getArgs().length; i++) {
            parameterTypes[i] = joinPoint.getArgs()[i].getClass();
        }
        return targetClass.getMethod(methodName, parameterTypes);
    }
}

