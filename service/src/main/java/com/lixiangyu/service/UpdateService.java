package com.lixiangyu.service;

import com.lixiangyu.common.util.UpdateResult;
import org.springframework.transaction.annotation.Transactional;

public interface UpdateService {

    UpdateResult batchUpdate(Integer count);

    /**
     * 执行一个循环操作
     */
    UpdateResult cycle(Integer count);

    UpdateResult insert(Integer count);

}
