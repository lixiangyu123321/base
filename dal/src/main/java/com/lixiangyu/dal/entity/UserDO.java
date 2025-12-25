package com.lixiangyu.dal.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import javax.persistence.Table;

/**
 * 用户实体类
 *
 * @author lixiangyu
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@Table(name = "t_user")
public class UserDO extends BaseDO {

    private static final long serialVersionUID = 1L;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 状态：0-禁用，1-启用
     */
    private Integer status;
}

