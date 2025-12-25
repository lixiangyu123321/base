package com.lixiangyu.service.impl;

import com.lixiangyu.common.exception.BusinessException;
import com.lixiangyu.dal.entity.UserDO;
import com.lixiangyu.mapper.UserMapper;
import com.lixiangyu.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * 用户服务实现类
 *
 * @author lixiangyu
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    @Override
    public UserDO getById(Long id) {
        if (id == null) {
            throw new BusinessException("用户ID不能为空");
        }
        return userMapper.selectByPrimaryKey(id);
    }

    @Override
    public UserDO getByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new BusinessException("用户名不能为空");
        }
        return userMapper.selectByUsername(username);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserDO save(UserDO user) {
        if (user == null) {
            throw new BusinessException("用户信息不能为空");
        }
        Date now = new Date();
        user.setCreateTime(now);
        user.setUpdateTime(now);
        userMapper.insertSelective(user);
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserDO update(UserDO user) {
        if (user == null || user.getId() == null) {
            throw new BusinessException("用户信息或ID不能为空");
        }
        user.setUpdateTime(new Date());
        userMapper.updateByPrimaryKeySelective(user);
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (id == null) {
            throw new BusinessException("用户ID不能为空");
        }
        userMapper.deleteByPrimaryKey(id);
    }
}

