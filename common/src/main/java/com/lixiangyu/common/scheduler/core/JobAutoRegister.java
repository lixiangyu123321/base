package com.lixiangyu.common.scheduler.core;

import com.lixiangyu.common.config.DynamicConfigManager;
import com.lixiangyu.common.scheduler.annotation.ScheduledJob;
import com.lixiangyu.common.scheduler.entity.JobConfig;
import com.lixiangyu.common.scheduler.service.JobConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 任务自动注册器
 * 自动扫描并注册实现了 Job 接口的 Bean
 * 
 * 功能特性：
 * 1. 自动扫描实现了 Job 接口的 Bean
 * 2. 支持 @ScheduledJob 注解配置
 * 3. 支持从数据库加载配置
 * 4. 自动注册到 Quartz 或 XXL-Job
 *
 * BeanPostProcessor 用于Bean初始化后的扫描
 * ApplicationContextAware 用于获取Spring容器
 * @author lixiangyu
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobAutoRegister implements BeanPostProcessor, ApplicationContextAware {
    
    private final JobConfigService jobConfigService;
    private final SchedulerManager schedulerManager;
    private final DynamicConfigManager configManager;
    private final Environment environment;
    private final JobConfigChangeListener configChangeListener;
    
    private ApplicationContext applicationContext;
    
    /**
     * 已注册的任务列表
     * 用于检查是否完成注册
     */
    private final List<JobRegistration> registeredJobs = new ArrayList<>();
    
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
    
    /**
     * 初始化：扫描所有已存在的 Job Bean
     */
    @PostConstruct
    public void init() {
        log.info("开始自动注册任务...");
        
        // 获取所有实现了 Job 接口的 Bean
        Map<String, Job> jobBeans = applicationContext.getBeansOfType(Job.class);
        
        for (Map.Entry<String, Job> entry : jobBeans.entrySet()) {
            String beanName = entry.getKey();
            Job job = entry.getValue();
            
            try {
                registerJob(beanName, job);
            } catch (Exception e) {
                log.error("自动注册任务失败，Bean Name: {}, Job Class: {}", 
                        beanName, job.getClass().getName(), e);
            }
        }
        
        log.info("任务自动注册完成，共注册 {} 个任务", registeredJobs.size());
    }

    /**
     * 兜底注册
     * 比如说延迟加载的Bean对象，只有在第一次请求时才做加载，此时init方法处无法获得对应的Bean了
     * Bean的创建时机可能晚于JobAutoRegister
     * @param bean bean对象
     * @param beanName bean名称
     * @return 返回增强后的Bean 这里为原bean对象
     * @throws BeansException 抛出Bean异常
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // 检查是否是 Job 接口的实现
        if (bean instanceof Job) {
            Job job = (Job) bean;
            
            // 检查是否已经注册过
            if (!isRegistered(beanName, job)) {
                try {
                    registerJob(beanName, job);
                } catch (Exception e) {
                    log.error("自动注册任务失败，Bean Name: {}, Job Class: {}", 
                            beanName, job.getClass().getName(), e);
                }
            }
        }
        
        return bean;
    }
    
    /**
     * 注册任务
     *
     * @param beanName Bean 名称
     * @param job 任务实例
     */
    private void registerJob(String beanName, Job job) {
        // 获取 @ScheduledJob 注解
        ScheduledJob annotation = AnnotationUtils.findAnnotation(job.getClass(), ScheduledJob.class);

        // 构建任务配置
        JobConfig config = buildJobConfig(annotation, job, beanName);
        
        if (config == null) {
            log.warn("构建任务配置失败，Bean Name: {}", beanName);
            return;
        }
        
        // 保存或更新配置到数据库
        JobConfig savedConfig = saveOrUpdateConfig(config);
        
        // 发布配置到 Nacos 配置中心
        publishConfigToNacos(savedConfig);
        
        // 注册配置变更监听器
        registerConfigListener(savedConfig);
        
        // 如果配置了自动启动，则启动任务
        if (annotation != null && annotation.autoStart() && savedConfig.getStatus() == JobConfig.JobStatus.RUNNING) {
            try {
                schedulerManager.addJob(savedConfig);
                log.info("任务自动注册并启动成功，Job Name: {}, Group: {}", 
                        savedConfig.getJobName(), savedConfig.getJobGroup());
            } catch (Exception e) {
                log.error("任务自动启动失败，Job Name: {}, Group: {}", 
                        savedConfig.getJobName(), savedConfig.getJobGroup(), e);
            }
        }
        
        // 记录已注册的任务
        registeredJobs.add(new JobRegistration(beanName, job, savedConfig));
    }
    
    /**
     * 构建任务配置
     *
     * @param annotation 注解
     * @param job 任务实例
     * @param beanName Bean 名称
     * @return 任务配置
     */
    private JobConfig buildJobConfig(ScheduledJob annotation, Job job, String beanName) {

        if(annotation == null){
            String environment = "dev";
            return JobConfig.builder()
                    .jobName(job.getClass().getName())
                    .jobGroup(job.getClass().getPackage().getName())
                    .jobType(JobConfig.JobType.QUARTZ)
                    .jobClass(job.getClass().getName())
                    .cronExpression(null)
                    .description("自动注册的任务")
                    .environment(environment)
                    .status(JobConfig.JobStatus.STOPPED)
                    .retryCount(3)
                    .retryInterval(60)
                    .alertEnabled(true)
                    .grayReleaseEnabled(false)
                    .version(1)
                    .build();
        }

        String jobName = annotation.jobName();
        String jobGroup = annotation.jobGroup();
        String environment = getEnvironment(annotation);
        
        JobConfig config = null;
        
        // 如果配置了从数据库加载，优先从数据库加载
        if (annotation.loadFromDatabase()) {
            config = jobConfigService.getByJobNameAndGroup(jobName, jobGroup, environment);
        }
        
        // 如果数据库中没有配置，使用注解配置创建新配置
        if (config == null) {
            config = JobConfig.builder()
                    .jobName(jobName)
                    .jobGroup(jobGroup)
                    .jobType(annotation.jobType())
                    .jobClass(job.getClass().getName())
                    .cronExpression(StringUtils.hasText(annotation.cronExpression()) 
                            ? annotation.cronExpression() : null)
                    .description(StringUtils.hasText(annotation.description()) 
                            ? annotation.description() : "自动注册的任务")
                    .environment(environment)
                    .status(annotation.autoStart() 
                            ? JobConfig.JobStatus.RUNNING 
                            : JobConfig.JobStatus.STOPPED)
                    .retryCount(3)
                    .retryInterval(60)
                    .alertEnabled(true)
                    .grayReleaseEnabled(false)
                    .version(1)
                    .build();
        } else {
            // 以任务(job)的全限定名作为JobName
            if (!job.getClass().getName().equals(config.getJobClass())) {
                config.setJobClass(job.getClass().getName());
            }
        }
        
        return config;
    }
    
    /**
     * 保存或更新配置
     *
     * @param config 任务配置
     * @return 保存后的配置
     */
    private JobConfig saveOrUpdateConfig(JobConfig config) {
        if (config.getId() == null) {
            return jobConfigService.save(config);
        } else {
            return jobConfigService.update(config);
        }
    }
    
    /**
     * 获取环境
     *
     * @param annotation 注解
     * @return 环境
     */
    private String getEnvironment(ScheduledJob annotation) {
        if (StringUtils.hasText(annotation.environment())) {
            return annotation.environment();
        }
        // 从配置中读取
        return environment.getProperty("spring.profiles.active", "dev");
    }
    
    /**
     * 发布配置到 Nacos 配置中心
     *
     * @param config 任务配置
     */
    private void publishConfigToNacos(JobConfig config) {
        try {
            // 构建配置 Data ID
            String dataId = buildNacosDataId(config);
            
            // 构建配置内容（JSON 格式）
            String configContent = buildNacosConfigContent(config);
            
            // 发布到 Nacos
            boolean success = configManager.publishConfig(configContent, dataId, "DEFAULT_GROUP");
            
            if (success) {
                log.info("任务配置已发布到 Nacos，Data ID: {}, Job Name: {}", dataId, config.getJobName());
            } else {
                log.warn("任务配置发布到 Nacos 失败，Data ID: {}, Job Name: {}", dataId, config.getJobName());
            }
        } catch (Exception e) {
            log.error("发布任务配置到 Nacos 异常，Job Name: {}", config.getJobName(), e);
        }
    }
    
    /**
     * 构建 Nacos Data ID
     *
     * @param config 任务配置
     * @return Data ID
     */
    private String buildNacosDataId(JobConfig config) {
        // 格式：scheduler.job.{jobName}.{jobGroup}.{environment}.json
        return String.format("scheduler.job.%s.%s.%s.json", 
                config.getJobName(), 
                config.getJobGroup(), 
                config.getEnvironment());
    }
    
    /**
     * 构建 Nacos 配置内容（JSON 格式）
     *
     * @param config 任务配置
     * @return JSON 配置内容
     */
    private String buildNacosConfigContent(JobConfig config) {
        com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
        json.put("jobName", config.getJobName());
        json.put("jobGroup", config.getJobGroup());
        json.put("jobType", config.getJobType() != null ? config.getJobType().name() : null);
        json.put("jobClass", config.getJobClass());
        json.put("cronExpression", config.getCronExpression());
        json.put("jobParams", config.getJobParams());
        json.put("description", config.getDescription());
        json.put("status", config.getStatus() != null ? config.getStatus().name() : null);
        json.put("environment", config.getEnvironment());
        json.put("retryCount", config.getRetryCount());
        json.put("retryInterval", config.getRetryInterval());
        json.put("timeout", config.getTimeout());
        json.put("alertEnabled", config.getAlertEnabled());
        json.put("alertTypes", config.getAlertTypes() != null 
                ? config.getAlertTypes().stream().map(Enum::name).collect(java.util.stream.Collectors.toList())
                : null);
        json.put("alertReceivers", config.getAlertReceivers());
        json.put("grayReleaseEnabled", config.getGrayReleaseEnabled());
        json.put("grayReleasePercent", config.getGrayReleasePercent());
        json.put("version", config.getVersion());
        return json.toJSONString();
    }
    
    /**
     * 注册配置变更监听器
     *
     * @param config 任务配置
     */
    private void registerConfigListener(JobConfig config) {
        try {
            configChangeListener.registerJobConfigListener(config);
        } catch (Exception e) {
            log.error("注册配置变更监听器失败，Job Name: {}", config.getJobName(), e);
        }
    }
    
    /**
     * 检查任务是否已注册
     *
     * @param beanName Bean 名称
     * @param job 任务实例
     * @return 是否已注册
     */
    private boolean isRegistered(String beanName, Job job) {
        return registeredJobs.stream()
                .anyMatch(reg -> reg.getBeanName().equals(beanName) 
                        && reg.getJob().getClass().equals(job.getClass()));
    }
    
    /**
     * 任务注册信息
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class JobRegistration {
        private String beanName;
        private Job job;
        private JobConfig config;
    }
}

