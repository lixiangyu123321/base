create database test;
use test;
CREATE TABLE `t_aigc_evaluating` (
                                     `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
                                     `tag` varchar(255) CHARACTER SET utf8mb4 DEFAULT NULL COMMENT '评测集标识',
                                     `type` varchar(255) CHARACTER SET utf8mb4 DEFAULT NULL COMMENT '类型',
                                     `img_url` varchar(255) CHARACTER SET utf8mb4 DEFAULT NULL COMMENT '图片url',
                                     `prompt` text COMMENT '提示词',
                                     `prompt_mills` bigint(20) DEFAULT NULL COMMENT '构建提示词耗时',
                                     `refer_key` varchar(255) DEFAULT NULL COMMENT '评测记录唯一标识',
                                     `model_code` varchar(255) DEFAULT NULL COMMENT '模型',
                                     `ret_url` varchar(255) DEFAULT NULL COMMENT '生成结果',
                                     `ellipse_mills` bigint(20) DEFAULT NULL COMMENT '生成耗时',
                                     `evaluate_result` text COMMENT '评测结果',
                                     `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                     `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
                                     `creator` varchar(255) NOT NULL DEFAULT '' COMMENT '创建人',
                                     `modifier` varchar(255) NOT NULL DEFAULT '' COMMENT '更新人',
                                     `status` int(10) DEFAULT '0' COMMENT '状态',
                                     PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=345 DEFAULT CHARSET=utf8 COMMENT='视频评测记录'