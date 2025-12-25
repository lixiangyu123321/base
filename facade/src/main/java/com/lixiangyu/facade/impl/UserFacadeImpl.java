package com.lixiangyu.facade.impl;

import com.lixiangyu.dal.entity.UserDO;
import com.lixiangyu.facade.UserFacade;
import com.lixiangyu.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 用户门面实现类
 *
 * @author lixiangyu
 */
@Component
@RequiredArgsConstructor
public class UserFacadeImpl implements UserFacade {

    private final UserService userService;

    @Override
    public UserDO getUserById(Long id) {
        return userService.getById(id);
    }

    @Override
    public UserDO getUserByUsername(String username) {
        return userService.getByUsername(username);
    }
}

