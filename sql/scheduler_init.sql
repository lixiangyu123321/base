-- 任务调度框架数据库初始化脚本

-- 1. 任务配置表
CREATE TABLE IF NOT EXISTS `scheduler_job_config` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `job_name` VARCHAR(255) NOT NULL COMMENT '任务名称',
  `job_group` VARCHAR(255) NOT NULL DEFAULT 'DEFAULT' COMMENT '任务分组',
  `job_type` VARCHAR(50) NOT NULL COMMENT '任务类型：QUARTZ/XXL_JOB',
  `job_class` VARCHAR(500) NOT NULL COMMENT '任务执行类',
  `cron_expression` VARCHAR(255) DEFAULT NULL COMMENT 'Cron表达式',
  `job_params` TEXT COMMENT '任务参数（JSON格式）',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '任务描述',
  `status` VARCHAR(20) NOT NULL DEFAULT 'STOPPED' COMMENT '任务状态：RUNNING/STOPPED/PAUSED',
  `environment` VARCHAR(50) NOT NULL DEFAULT 'dev' COMMENT '环境：dev/test/prod',
  `retry_count` INT(11) NOT NULL DEFAULT 3 COMMENT '重试次数',
  `retry_interval` INT(11) NOT NULL DEFAULT 60 COMMENT '重试间隔（秒）',
  `timeout` INT(11) DEFAULT NULL COMMENT '超时时间（秒）',
  `alert_enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用告警',
  `alert_types` VARCHAR(255) DEFAULT NULL COMMENT '告警类型：DINGTALK/WECHAT/EMAIL（逗号分隔）',
  `alert_receivers` TEXT COMMENT '告警接收人（JSON格式）',
  `gray_release_enabled` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用灰度发布',
  `gray_release_percent` INT(11) DEFAULT 0 COMMENT '灰度发布百分比（0-100）',
  `version` INT(11) NOT NULL DEFAULT 1 COMMENT '版本号',
  `creator` VARCHAR(100) DEFAULT NULL COMMENT '创建人',
  `modifier` VARCHAR(100) DEFAULT NULL COMMENT '修改人',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_name_group` (`job_name`, `job_group`, `environment`),
  KEY `idx_status` (`status`),
  KEY `idx_environment` (`environment`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务配置表';

-- 2. 任务执行日志表
CREATE TABLE IF NOT EXISTS `scheduler_job_log` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `job_id` BIGINT(20) NOT NULL COMMENT '任务ID',
  `job_name` VARCHAR(255) NOT NULL COMMENT '任务名称',
  `job_group` VARCHAR(255) NOT NULL COMMENT '任务分组',
  `trigger_name` VARCHAR(255) DEFAULT NULL COMMENT '触发器名称',
  `trigger_group` VARCHAR(255) DEFAULT NULL COMMENT '触发器分组',
  `execution_id` VARCHAR(100) NOT NULL COMMENT '执行ID',
  `start_time` DATETIME NOT NULL COMMENT '开始时间',
  `end_time` DATETIME DEFAULT NULL COMMENT '结束时间',
  `duration` BIGINT(20) DEFAULT NULL COMMENT '执行时长（毫秒）',
  `status` VARCHAR(20) NOT NULL COMMENT '执行状态：SUCCESS/FAILED/RUNNING',
  `retry_count` INT(11) NOT NULL DEFAULT 0 COMMENT '重试次数',
  `error_message` TEXT COMMENT '错误信息',
  `execution_log` LONGTEXT COMMENT '执行日志',
  `server_ip` VARCHAR(50) DEFAULT NULL COMMENT '执行服务器IP',
  `server_name` VARCHAR(255) DEFAULT NULL COMMENT '执行服务器名称',
  PRIMARY KEY (`id`),
  KEY `idx_job_id` (`job_id`),
  KEY `idx_execution_id` (`execution_id`),
  KEY `idx_start_time` (`start_time`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务执行日志表';

-- 3. 任务告警记录表
CREATE TABLE IF NOT EXISTS `scheduler_job_alert` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `job_id` BIGINT(20) NOT NULL COMMENT '任务ID',
  `log_id` BIGINT(20) DEFAULT NULL COMMENT '日志ID',
  `alert_type` VARCHAR(50) NOT NULL COMMENT '告警类型：DINGTALK/WECHAT/EMAIL',
  `alert_content` TEXT NOT NULL COMMENT '告警内容',
  `alert_status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '告警状态：PENDING/SENT/FAILED',
  `send_time` DATETIME DEFAULT NULL COMMENT '发送时间',
  `error_message` TEXT COMMENT '发送错误信息',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_job_id` (`job_id`),
  KEY `idx_log_id` (`log_id`),
  KEY `idx_alert_status` (`alert_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务告警记录表';

-- 4. 灰度发布记录表
CREATE TABLE IF NOT EXISTS `scheduler_gray_release` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `job_id` BIGINT(20) NOT NULL COMMENT '任务ID',
  `from_version` INT(11) NOT NULL COMMENT '源版本',
  `to_version` INT(11) NOT NULL COMMENT '目标版本',
  `gray_percent` INT(11) NOT NULL COMMENT '灰度百分比',
  `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/RUNNING/SUCCESS/FAILED',
  `start_time` DATETIME DEFAULT NULL COMMENT '开始时间',
  `end_time` DATETIME DEFAULT NULL COMMENT '结束时间',
  `creator` VARCHAR(100) DEFAULT NULL COMMENT '创建人',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_job_id` (`job_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='灰度发布记录表';

-- 5. 权限管控表
CREATE TABLE IF NOT EXISTS `scheduler_permission` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` VARCHAR(100) NOT NULL COMMENT '用户ID',
  `username` VARCHAR(100) NOT NULL COMMENT '用户名',
  `role` VARCHAR(50) NOT NULL COMMENT '角色：ADMIN/OPERATOR/VIEWER',
  `permissions` TEXT COMMENT '权限列表（JSON格式）',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/INACTIVE',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`),
  KEY `idx_role` (`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限管控表';

-- 6. 配置变更记录表
CREATE TABLE IF NOT EXISTS `scheduler_config_change` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `job_id` BIGINT(20) NOT NULL COMMENT '任务ID',
  `change_type` VARCHAR(50) NOT NULL COMMENT '变更类型：CREATE/UPDATE/DELETE',
  `old_config` TEXT COMMENT '旧配置（JSON格式）',
  `new_config` TEXT COMMENT '新配置（JSON格式）',
  `operator` VARCHAR(100) DEFAULT NULL COMMENT '操作人',
  `operator_ip` VARCHAR(50) DEFAULT NULL COMMENT '操作IP',
  `change_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '变更时间',
  PRIMARY KEY (`id`),
  KEY `idx_job_id` (`job_id`),
  KEY `idx_change_time` (`change_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配置变更记录表';

