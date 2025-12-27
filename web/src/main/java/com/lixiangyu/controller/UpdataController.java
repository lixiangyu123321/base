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


    /**
     * 数据量在2000以上更有优势
     * @param jsonObject
     * @return
     */
    @RequestMapping("cycle")
    public UpdateResult cycle(@RequestBody JSONObject jsonObject){
        int count = jsonObject.getAsNumber("count").intValue();
        return updateService.cycle(count);
    }


    @RequestMapping("batchUpdata")
    public UpdateResult batchUpdate(@RequestBody JSONObject jsonObject){
        int count = jsonObject.getAsNumber("count").intValue();
        return updateService.batchUpdate(count);
    }

    @RequestMapping("/insert")
    public UpdateResult insert(@RequestBody JSONObject jsonObject){
        int count = jsonObject.getAsNumber("count").intValue();
        return updateService.insert(count);
    }

    @RequestMapping("/cycleWithThreadPool")
    public UpdateResult cycleWithThreadPool(@RequestBody JSONObject jsonObject){
        int count = jsonObject.getAsNumber("count").intValue();
        int batchSize = jsonObject.getAsNumber("batchSize").intValue();
        return updateService.cycleWithThreadPool(count, batchSize);
    }

    @RequestMapping("/batchUpdateWithThreadPool")
    public UpdateResult batchUpdateWithThreadPool(@RequestBody JSONObject jsonObject){
        int count = jsonObject.getAsNumber("count").intValue();
        int batchSize = jsonObject.getAsNumber("batchSize").intValue();
        return updateService.batchUpdateWithThreadPool(count, batchSize);
    }
}
