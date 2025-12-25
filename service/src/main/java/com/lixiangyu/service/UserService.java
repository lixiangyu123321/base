package com.lixiangyu.service;

import com.lixiangyu.dal.entity.UserDO;

/**
 * 用户服务接口
 *
 * @author lixiangyu
 */
public interface UserService {

    /**
     * 根据ID查询用户
     *
     * @param id 用户ID
     * @return 用户信息
     */
    UserDO getById(Long id);

    /**
     * 根据用户名查询用户
     *
     * @param username 用户名
     * @return 用户信息
     */
    UserDO getByUsername(String username);

    /**
     * 保存用户
     *
     * @param user 用户信息
     * @return 保存后的用户信息
     */
    UserDO save(UserDO user);

    /**
     * 更新用户
     *
     * @param user 用户信息
     * @return 更新后的用户信息
     */
    UserDO update(UserDO user);

    /**
     * 删除用户
     *
     * @param id 用户ID
     */
    void delete(Long id);
}

