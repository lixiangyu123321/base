package com.lixiangyu.dal.entity;

import lombok.Data;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("t_aigc_evaluating")
public class EvaluatingDO extends BaseDO{
    private String tag;
    private String type;
    private String imgUrl;
    private String prompt;
    /**
     * 构建提示词耗时
     */
    private Long promptMills;
    private String referKey;

    /**
     * 模型
     */
    private String modelCode;
    /**
     * 生成结果
     */
    private String retUrl;
    /**
     * 生成耗时
     */
    private Long ellipseMills;


    private String evaluateResult;
}

