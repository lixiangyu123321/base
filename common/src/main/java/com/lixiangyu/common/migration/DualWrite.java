package com.lixiangyu.common.migration;

import java.lang.annotation.*;

/**
 * 双写注解
 * 用于标记需要同时写入源库和目标库的方法
 * 
 * 使用场景：
 * - 数据库迁移期间，需要同时写入源库和目标库
 * - 逐步切换流量时，保证数据一致性
 * 
 * 使用示例：
 * <pre>
 * {@code
 * @DualWrite(
 *     source = "sourceDataSource",
 *     target = "targetDataSource",
 *     tables = {"user", "order"},
 *     rollbackOnFailure = true
 * )
 * public void saveUser(User user) {
 *     // 业务逻辑
 * }
 * }
 * </pre>
 * 
 * @author lixiangyu
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DualWrite {
    
    /**
     * 源数据源 Bean 名称
     * 如果为空，使用默认数据源
     */
    String source() default "";
    
    /**
     * 目标数据源 Bean 名称
     * 如果为空，使用默认数据源
     */
    String target() default "";
    
    /**
     * 要双写的表列表
     * 如果为空，则对所有表进行双写
     */
    String[] tables() default {};
    
    /**
     * 是否在失败时回滚
     * true: 如果目标库写入失败，回滚源库操作
     * false: 只记录错误，不回滚
     */
    boolean rollbackOnFailure() default true;
    
    /**
     * 写入顺序
     * SOURCE_FIRST: 先写源库，再写目标库
     * TARGET_FIRST: 先写目标库，再写源库
     * PARALLEL: 并行写入
     */
    WriteOrder order() default WriteOrder.SOURCE_FIRST;
    
    /**
     * 写入顺序枚举
     */
    enum WriteOrder {
        /**
         * 先写源库
         */
        SOURCE_FIRST,
        
        /**
         * 先写目标库
         */
        TARGET_FIRST,
        
        /**
         * 并行写入
         */
        PARALLEL
    }
    
    /**
     * 是否异步写入目标库
     * true: 异步写入，不阻塞主流程
     * false: 同步写入
     */
    boolean async() default false;
    
    /**
     * 失败重试次数
     */
    int retryTimes() default 3;
    
    /**
     * 重试间隔（毫秒）
     */
    long retryInterval() default 1000;
}

