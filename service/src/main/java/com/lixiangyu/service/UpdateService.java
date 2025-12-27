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

    /**
     * 通过线程池分批次在循环内更新
     *
     * @param count 更新数量
     * @return 更新结果
     */
    UpdateResult cycleWithThreadPool(Integer count, Integer batchSize);

    /**
     * 通过线程池批量更新
     *
     * @param count 更新数量
     * @return 更新结果
     */
    UpdateResult batchUpdateWithThreadPool(Integer count, Integer batchSize);

}
