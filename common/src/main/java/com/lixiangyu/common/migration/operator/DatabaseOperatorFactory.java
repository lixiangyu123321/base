package com.lixiangyu.common.migration.operator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * 数据库操作器工厂
 * 根据配置创建不同的数据库操作器
 * 
 * @author lixiangyu
 */
@Slf4j
@Component
public class DatabaseOperatorFactory {

    /**
     * 非必须的依赖
     */
    @Autowired(required = false)
    private ApplicationContext applicationContext;
    
    /**
     * 创建数据库操作器
     *
     * @param dataSource 数据源
     * @param operatorType 操作器类型
     * @return 数据库操作器
     */
    public DatabaseOperator createOperator(DataSource dataSource, OperatorType operatorType) {
        switch (operatorType) {
            case JDBC:
                return new JdbcDatabaseOperator(dataSource);
                
            case JDBC_TEMPLATE:
                return new JdbcTemplateDatabaseOperator(dataSource);
                
            case MYBATIS:
                try {
                    // 尝试从 Spring 容器获取 MyBatis 相关 Bean（使用反射避免直接依赖）
                    if (applicationContext != null) {
                        try {
                            // 使用反射获取 MyBatis Bean
                            Class<?> sqlSessionFactoryClass = Class.forName("org.apache.ibatis.session.SqlSessionFactory");
                            Class<?> sqlSessionTemplateClass = Class.forName("org.mybatis.spring.SqlSessionTemplate");
                            
                            Object sqlSessionFactory = applicationContext.getBean(sqlSessionFactoryClass);
                            Object sqlSessionTemplate = applicationContext.getBean(sqlSessionTemplateClass);
                            
                            return new MyBatisDatabaseOperator(sqlSessionFactory, sqlSessionTemplate, dataSource);
                        } catch (ClassNotFoundException e) {
                            log.warn("MyBatis 未配置，降级使用 JdbcTemplate", e);
                            return new JdbcTemplateDatabaseOperator(dataSource);
                        } catch (Exception e) {
                            log.warn("MyBatis Bean 获取失败，降级使用 JdbcTemplate", e);
                            return new JdbcTemplateDatabaseOperator(dataSource);
                        }
                    }
                } catch (Exception e) {
                    log.warn("MyBatis 未配置，降级使用 JdbcTemplate", e);
                    return new JdbcTemplateDatabaseOperator(dataSource);
                }
                
            case JPA:
                try {
                    // 尝试从 Spring 容器获取 JPA 相关 Bean（使用反射避免直接依赖）
                    if (applicationContext != null) {
                        try {
                            Class<?> entityManagerClass = Class.forName("javax.persistence.EntityManager");
                            Class<?> entityManagerFactoryClass = Class.forName("javax.persistence.EntityManagerFactory");
                            
                            Object entityManager = applicationContext.getBean(entityManagerClass);
                            Object entityManagerFactory = applicationContext.getBean(entityManagerFactoryClass);
                            
                            return new JpaDatabaseOperator(entityManager, entityManagerFactory, dataSource);
                        } catch (ClassNotFoundException e) {
                            log.warn("JPA 未配置，降级使用 JdbcTemplate", e);
                            return new JdbcTemplateDatabaseOperator(dataSource);
                        } catch (Exception e) {
                            log.warn("JPA Bean 获取失败，降级使用 JdbcTemplate", e);
                            return new JdbcTemplateDatabaseOperator(dataSource);
                        }
                    }
                } catch (Exception e) {
                    log.warn("JPA 未配置，降级使用 JdbcTemplate", e);
                    return new JdbcTemplateDatabaseOperator(dataSource);
                }
                
            default:
                return new JdbcDatabaseOperator(dataSource);
        }
    }
    
    /**
     * 操作器类型枚举
     */
    public enum OperatorType {
        /**
         * JDBC 原生接口
         */
        JDBC,
        
        /**
         * Spring JDBC Template
         */
        JDBC_TEMPLATE,
        
        /**
         * MyBatis
         */
        MYBATIS,
        
        /**
         * JPA
         */
        JPA
    }
}

