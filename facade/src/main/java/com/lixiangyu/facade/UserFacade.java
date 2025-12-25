package com.lixiangyu.facade;

import com.lixiangyu.dal.entity.UserDO;

/**
 * 用户门面接口
 * 对外提供统一的服务接口
 *
 * @author lixiangyu
 */
public interface UserFacade {

    /**
     * 根据ID查询用户
     *
     * @param id 用户ID
     * @return 用户信息
     */
    UserDO getUserById(Long id);

    /**
     * 根据用户名查询用户
     *
     * @param username 用户名
     * @return 用户信息
     */
    UserDO getUserByUsername(String username);
}

