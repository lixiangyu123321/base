package com.lixiangyu.controller;

import javax.annotation.Resource;

import com.lixiangyu.common.util.UpdateResult;
import com.nimbusds.jose.shaded.json.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lixiangyu.service.UpdateService;

@Slf4j
@RestController
@RequestMapping("/api/update")
@RequiredArgsConstructor
public class UpdataController {

    @Resource
    private UpdateService updateService;


    @RequestMapping("cycle")
    public void cycle(){
    }


    @RequestMapping("batchUpdata")
    public void batchUpdate(){

    }

    @RequestMapping("/insert")
    public UpdateResult insert(@RequestBody JSONObject jsonObject){
        int count = jsonObject.getAsNumber("count").intValue();
        log.info("count:"+count);
        return updateService.insert(count);
    }
}
