package com.lixiangyu;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 应用启动类
 *
 * @author lixiangyu
 */
@SpringBootApplication(scanBasePackages = "com.lixiangyu")
@MapperScan("com.lixiangyu.dal.entity.mapper")
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}

