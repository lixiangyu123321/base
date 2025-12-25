package com.lixiangyu.service;

import com.lixiangyu.dal.entity.UserDO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 用户服务测试类
 *
 * @author lixiangyu
 */
@SpringBootTest
@ActiveProfiles("dev")
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Test
    void testGetById() {
        // 测试根据ID查询用户
        // UserDO user = userService.getById(1L);
        // assertNotNull(user);
    }

    @Test
    void testSave() {
        // 测试保存用户
        // UserDO user = UserDO.builder()
        //         .username("test")
        //         .email("test@example.com")
        //         .status(1)
        //         .build();
        // UserDO saved = userService.save(user);
        // assertNotNull(saved.getId());
    }
}

