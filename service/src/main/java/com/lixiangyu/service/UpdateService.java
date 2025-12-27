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

    /**
     * 基于游标的批量更新（基于主键ID，避免一次性加载所有数据到内存）
     *
     * @param batchSize 每批处理的数量
     * @param maxCount 最大更新数量，null表示不限制
     * @return 更新结果
     */
    UpdateResult batchUpdateByCursorId(Integer batchSize, Integer maxCount);

    /**
     * 基于游标的批量更新（基于创建时间，避免一次性加载所有数据到内存）
     *
     * @param batchSize 每批处理的数量
     * @param maxCount 最大更新数量，null表示不限制
     * @return 更新结果
     */
    UpdateResult batchUpdateByCursorTime(Integer batchSize, Integer maxCount);

    /**
     * 基于游标的线程池批量更新（基于主键ID，结合游标和线程池的优势）
     *
     * @param batchSize 每批处理的数量
     * @param threadBatchSize 线程池中每个任务的批次大小
     * @param maxCount 最大更新数量，null表示不限制
     * @return 更新结果
     */
    UpdateResult batchUpdateByCursorIdWithThreadPool(Integer batchSize, Integer threadBatchSize, Integer maxCount);

}
