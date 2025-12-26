package com.lixiangyu.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

/**
 * Spring Security 配置类
 * 
 * 解决接口访问被重定向到登录页的问题
 * 默认情况下，Spring Security 会拦截所有请求并要求认证
 * 此配置允许所有 API 接口无需认证即可访问
 *
 * @author lixiangyu
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            // 禁用 CSRF（跨站请求伪造）保护，方便 API 测试
            .csrf().disable()
            // 配置请求授权
            .authorizeRequests()
                // 允许所有请求无需认证
                .anyRequest().permitAll();
    }
}
