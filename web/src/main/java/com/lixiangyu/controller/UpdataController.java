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

    /**
     * 基于游标ID的批量更新（避免一次性加载所有数据到内存）
     * @param jsonObject 包含 batchSize（每批查询数量）和 maxCount（最大更新数量，可选）
     * @return 更新结果
     */
    @RequestMapping("/batchUpdateByCursorId")
    public UpdateResult batchUpdateByCursorId(@RequestBody JSONObject jsonObject){
        Integer batchSize = jsonObject.containsKey("batchSize") ? 
                jsonObject.getAsNumber("batchSize").intValue() : 1000;
        Integer maxCount = jsonObject.containsKey("maxCount") ? 
                jsonObject.getAsNumber("maxCount").intValue() : null;
        return updateService.batchUpdateByCursorId(batchSize, maxCount);
    }

    /**
     * 基于游标时间的批量更新（避免一次性加载所有数据到内存）
     * @param jsonObject 包含 batchSize（每批查询数量）和 maxCount（最大更新数量，可选）
     * @return 更新结果
     */
    @RequestMapping("/batchUpdateByCursorTime")
    public UpdateResult batchUpdateByCursorTime(@RequestBody JSONObject jsonObject){
        Integer batchSize = jsonObject.containsKey("batchSize") ? 
                jsonObject.getAsNumber("batchSize").intValue() : 1000;
        Integer maxCount = jsonObject.containsKey("maxCount") ? 
                jsonObject.getAsNumber("maxCount").intValue() : null;
        return updateService.batchUpdateByCursorTime(batchSize, maxCount);
    }

    /**
     * 基于游标ID的线程池批量更新（结合游标和线程池的优势）
     * @param jsonObject 包含 batchSize（游标查询批次大小）、threadBatchSize（线程池任务批次大小）和 maxCount（最大更新数量，可选）
     * @return 更新结果
     */
    @RequestMapping("/batchUpdateByCursorIdWithThreadPool")
    public UpdateResult batchUpdateByCursorIdWithThreadPool(@RequestBody JSONObject jsonObject){
        Integer batchSize = jsonObject.containsKey("batchSize") ? 
                jsonObject.getAsNumber("batchSize").intValue() : 5000;
        Integer threadBatchSize = jsonObject.containsKey("threadBatchSize") ? 
                jsonObject.getAsNumber("threadBatchSize").intValue() : 1000;
        Integer maxCount = jsonObject.containsKey("maxCount") ? 
                jsonObject.getAsNumber("maxCount").intValue() : null;
        return updateService.batchUpdateByCursorIdWithThreadPool(batchSize, threadBatchSize, maxCount);
    }
}
