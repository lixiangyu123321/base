package com.lixiangyu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import tk.mybatis.spring.annotation.MapperScan;

/**
 * 应用启动类
 *
 * @author lixiangyu
 */
@SpringBootApplication(scanBasePackages = "com.lixiangyu")
@MapperScan(basePackages = "com.lixiangyu.dal.mapper", markerInterface = tk.mybatis.mapper.common.Mapper.class)
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}

