package com.lixiangyu.dal.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import javax.persistence.Table;

/**
 * 评测记录实体类
 *
 * @author lixiangyu
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "t_aigc_evaluating")
public class EvaluatingDO extends BaseDO {

    private static final long serialVersionUID = 1L;
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

