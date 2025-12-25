package com.lixiangyu.controller;

import com.lixiangyu.common.util.Result;
import com.lixiangyu.dal.entity.UserDO;
import com.lixiangyu.facade.UserFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户控制器
 *
 * @author lixiangyu
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserFacade userFacade;

    /**
     * 根据ID查询用户
     *
     * @param id 用户ID
     * @return 用户信息
     */
    @GetMapping("/{id}")
    public Result<UserDO> getUserById(@PathVariable Long id) {
        UserDO user = userFacade.getUserById(id);
        return Result.success(user);
    }

    /**
     * 根据用户名查询用户
     *
     * @param username 用户名
     * @return 用户信息
     */
    @GetMapping("/username/{username}")
    public Result<UserDO> getUserByUsername(@PathVariable String username) {
        UserDO user = userFacade.getUserByUsername(username);
        return Result.success(user);
    }

    /**
     * 健康检查接口
     *
     * @return 健康状态
     */
    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("服务运行正常");
    }
}

