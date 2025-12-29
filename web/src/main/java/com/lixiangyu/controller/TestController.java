package com.lixiangyu.controller;

import com.alibaba.fastjson.JSONObject;
import com.lixiangyu.common.config.DynamicConfigManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Properties;

@Slf4j
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class TestController {

    @Value("${test.config:lixiangyu}")
    private String message;

    @Autowired
    private DynamicConfigManager dynamicConfigManager;


    @RequestMapping("/hello")
    public JSONObject hello() {
        String var1 = message;
        String var2 = dynamicConfigManager.getString("test.config2", "test");
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("var1", var1);
        jsonObject.put("var2", var2);
        return jsonObject;
    }
}
