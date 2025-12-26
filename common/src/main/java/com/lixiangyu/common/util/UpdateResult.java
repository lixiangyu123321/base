package com.lixiangyu.common.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 更新操作结果封装
 *
 * @author lixiangyu
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 更新的记录数
     */
    private Integer updatedCount;

    /**
     * 执行时间（毫秒）
     */
    private Long executionTime;

    /**
     * 执行时间（秒）
     */
    private Double executionTimeSeconds;

    /**
     * 更新方式
     */
    private String updateMethod;
}

