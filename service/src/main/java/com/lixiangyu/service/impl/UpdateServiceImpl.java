package com.lixiangyu.service.impl;

import com.lixiangyu.common.util.UpdateResult;
import com.lixiangyu.dal.entity.EvaluatingDO;
import com.lixiangyu.dal.mapper.EvaluatingMapper;
import com.lixiangyu.service.UpdateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tk.mybatis.mapper.entity.Example;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 更新服务实现类
 *
 * @author lixiangyu
 */
@Slf4j
@Service
public class UpdateServiceImpl implements UpdateService {

    private final EvaluatingMapper evaluatingMapper;
    private final Executor batchUpdateExecutor;
    
    public UpdateServiceImpl(EvaluatingMapper evaluatingMapper, 
                             @Qualifier("batchUpdateExecutor") Executor batchUpdateExecutor) {
        this.evaluatingMapper = evaluatingMapper;
        this.batchUpdateExecutor = batchUpdateExecutor;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UpdateResult insert(Integer count) {
        long startTime = System.currentTimeMillis();
        
        Date now = new Date();
        int totalCount = count;
        int batchSize = 500;
        int insertCount = 0;
        
        // 分批插入，避免一次性插入过多数据
        for (int batch = 0; batch < (totalCount + batchSize - 1) / batchSize; batch++) {
            List<EvaluatingDO> evaluatingList = new ArrayList<>();
            int start = batch * batchSize + 1;
            int end = Math.min((batch + 1) * batchSize, totalCount);
            
            // 生成当前批次的数据
            for (int i = start; i <= end; i++) {
                EvaluatingDO evaluating = EvaluatingDO.builder()
                        .tag("test_tag_" + i)
                        .type("test_type_" + (i % 3))
                        .imgUrl("https://example.com/image_" + i + ".jpg")
                        .prompt("测试提示词_" + i + "：这是一个用于测试的提示词内容")
                        .promptMills(100L + i)
                        .referKey("refer_key_" + i)
                        .modelCode("model_" + (i % 5))
                        .retUrl("https://example.com/result_" + i + ".jpg")
                        .ellipseMills(200L + i * 10)
                        .evaluateResult("测试评测结果_" + i)
                        .creator("system")
                        .modifier("system")
                        .createTime(now)
                        .updateTime(now)
                        .remark("测试数据_" + i)
                        .build();
                evaluatingList.add(evaluating);
            }
            
            // 批量插入当前批次（使用自定义批量插入方法，避免 TK MyBatis insertList 的兼容性问题）
            int batchCount = evaluatingMapper.batchInsert(evaluatingList);
            insertCount += batchCount;
            log.debug("插入第{}批，{}条数据", batch + 1, batchCount);
        }
        
        long endTime = System.currentTimeMillis();
        long executionTime = endTime - startTime;
        
        log.info("插入{}条数据，耗时：{}ms", insertCount, executionTime);
        
        return new UpdateResult(insertCount, executionTime, executionTime / 1000.0, "批量插入");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UpdateResult cycle(Integer count) {
        if (count == null || count <= 0) {
            count = 100;
        }
        
        long startTime = System.currentTimeMillis();
        
        // 查询需要更新的数据（取前count条）
        Example example = new Example(EvaluatingDO.class);
        example.setOrderByClause("id ASC");
        List<EvaluatingDO> evaluatingList = evaluatingMapper.selectByExample(example);
        
        // 限制更新数量
        int updateCount = Math.min(count, evaluatingList.size());
        int actualUpdated = 0;
        
        // 循环更新每条记录
        for (int i = 0; i < updateCount; i++) {
            EvaluatingDO evaluating = evaluatingList.get(i);
            // 更新字段
            evaluating.setEllipseMills(evaluating.getEllipseMills() == null ? 100L : evaluating.getEllipseMills() + 100);
            evaluating.setEvaluateResult("循环更新_" + System.currentTimeMillis());
            evaluating.setUpdateTime(new Date());
            evaluating.setModifier("cycle_update");
            evaluating.setRemark("循环更新_" + System.currentTimeMillis());
            
            // 使用通用Mapper的updateByPrimaryKeySelective方法
            int result = evaluatingMapper.updateByPrimaryKeySelective(evaluating);
            if (result > 0) {
                actualUpdated++;
            }
        }
        
        long endTime = System.currentTimeMillis();
        long executionTime = endTime - startTime;
        
        log.info("循环更新{}条数据，实际更新{}条，耗时：{}ms", count, actualUpdated, executionTime);
        
        return new UpdateResult(actualUpdated, executionTime, executionTime / 1000.0, "循环更新");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UpdateResult batchUpdate(Integer count) {
        if (count == null || count <= 0) {
            count = 100;
        }
        
        long startTime = System.currentTimeMillis();
        
        // 查询需要更新的数据（取前count条）
        Example example = new Example(EvaluatingDO.class);
        example.setOrderByClause("id ASC");
        List<EvaluatingDO> evaluatingList = evaluatingMapper.selectByExample(example);
        
        // 限制更新数量
        int updateCount = Math.min(count, evaluatingList.size());
        
        if (updateCount == 0) {
            return new UpdateResult(0, 0L, 0.0, "批量更新");
        }

        List<EvaluatingDO> DOSToUpdate = evaluatingList.stream().limit(updateCount).collect(Collectors.toList());

        // 设置更新时间和更新人
        Date updateTime = new Date();
        for (EvaluatingDO evaluating : DOSToUpdate) {
            evaluating.setUpdateTime(updateTime);
            evaluating.setModifier("batch_update");
            evaluating.setRemark("批量更新_" + System.currentTimeMillis());
        }

        // 使用动态SQL批量更新
        int actualUpdated = evaluatingMapper.batchUpdate(DOSToUpdate);
        
        long endTime = System.currentTimeMillis();
        long executionTime = endTime - startTime;
        
        log.info("批量更新{}条数据，实际更新{}条，耗时：{}ms", count, actualUpdated, executionTime);
        
        return new UpdateResult(actualUpdated, executionTime, executionTime / 1000.0, "批量更新");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UpdateResult cycleWithThreadPool(Integer count, Integer batchSize) {
        if (count == null || count <= 0) {
            count = 100;
        }
        
        long startTime = System.currentTimeMillis();
        
        // 查询需要更新的数据（取前count条）
        Example example = new Example(EvaluatingDO.class);
        example.setOrderByClause("id ASC");
        List<EvaluatingDO> evaluatingList = evaluatingMapper.selectByExample(example);
        
        // 限制更新数量
        int updateCount = Math.min(count, evaluatingList.size());
        
        if (updateCount == 0) {
            return new UpdateResult(0, 0L, 0.0, "线程池循环更新");
        }
        
        // 每批处理的数据量
        int batchCount = (updateCount + batchSize - 1) / batchSize;
        
        // 使用 CountDownLatch 等待所有任务完成
        CountDownLatch latch = new CountDownLatch(batchCount);
        AtomicInteger actualUpdated = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        
        // 分批提交任务到线程池
        for (int batch = 0; batch < batchCount; batch++) {
            final int batchIndex = batch;
            final int start = batch * batchSize;
            final int end = Math.min((batch + 1) * batchSize, updateCount);
            
            batchUpdateExecutor.execute(() -> {
                try {
                    int batchUpdated = 0;
                    // 在当前批次内循环更新
                    for (int i = start; i < end; i++) {
                        EvaluatingDO evaluating = evaluatingList.get(i);
                        // 更新字段
                        evaluating.setEllipseMills(evaluating.getEllipseMills() == null ? 100L : evaluating.getEllipseMills() + 100);
                        evaluating.setEvaluateResult("线程池循环更新_" + System.currentTimeMillis() + "_" + Thread.currentThread().getName());
                        evaluating.setUpdateTime(new Date());
                        evaluating.setModifier("cycle_thread_pool_update");
                        evaluating.setRemark("线程池循环更新_" + System.currentTimeMillis());
                        
                        // 使用通用Mapper的updateByPrimaryKeySelective方法
                        int result = evaluatingMapper.updateByPrimaryKeySelective(evaluating);
                        if (result > 0) {
                            batchUpdated++;
                        }
                    }
                    actualUpdated.addAndGet(batchUpdated);
                    //log.debug("线程池批次{}完成，更新{}条数据", batchIndex + 1, batchUpdated);
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    //log.error("线程池批次{}更新失败", batchIndex + 1, e);
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // 等待所有任务完成
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            //log.error("等待线程池任务完成时被中断", e);
        }
        
        long endTime = System.currentTimeMillis();
        long executionTime = endTime - startTime;
        
        //log.info("线程池循环更新{}条数据，实际更新{}条，失败{}条，耗时：{}ms",
        //        count, actualUpdated.get(), errorCount.get(), executionTime);
        
        return new UpdateResult(actualUpdated.get(), executionTime, executionTime / 1000.0, "线程池循环更新");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UpdateResult batchUpdateWithThreadPool(Integer count, Integer batchSize) {
        if (count == null || count <= 0) {
            count = 100;
        }
        
        long startTime = System.currentTimeMillis();
        
        // 查询需要更新的数据（取前count条）
        Example example = new Example(EvaluatingDO.class);
        example.setOrderByClause("id ASC");
        List<EvaluatingDO> evaluatingList = evaluatingMapper.selectByExample(example);
        
        // 限制更新数量
        int updateCount = Math.min(count, evaluatingList.size());
        
        if (updateCount == 0) {
            return new UpdateResult(0, 0L, 0.0, "线程池批量更新");
        }
        
        // 每批处理的数据量
        int batchCount = (updateCount + batchSize - 1) / batchSize;
        
        // 使用 CountDownLatch 等待所有任务完成
        CountDownLatch latch = new CountDownLatch(batchCount);
        AtomicInteger actualUpdated = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        
        // 分批提交任务到线程池
        for (int batch = 0; batch < batchCount; batch++) {
            final int batchIndex = batch;
            final int start = batch * batchSize;
            final int end = Math.min((batch + 1) * batchSize, updateCount);
            
            batchUpdateExecutor.execute(() -> {
                try {
                    // 获取当前批次的数据
                    List<EvaluatingDO> batchList = new ArrayList<>();
                    for (int i = start; i < end; i++) {
                        EvaluatingDO evaluating = evaluatingList.get(i);
                        // 设置更新字段
                        evaluating.setUpdateTime(new Date());
                        evaluating.setModifier("batch_thread_pool_update");
                        evaluating.setRemark("线程池批量更新_" + System.currentTimeMillis() + "_" + Thread.currentThread().getName());
                        batchList.add(evaluating);
                    }
                    
                    // 使用动态SQL批量更新
                    int batchUpdated = evaluatingMapper.batchUpdate(batchList);
                    actualUpdated.addAndGet(batchUpdated);
//                    log.debug("线程池批次{}完成，批量更新{}条数据", batchIndex + 1, batchUpdated);
                } catch (Exception e) {
                    errorCount.incrementAndGet();
//                    log.error("线程池批次{}批量更新失败", batchIndex + 1, e);
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // 等待所有任务完成
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
//            log.error("等待线程池任务完成时被中断", e);
        }
        
        long endTime = System.currentTimeMillis();
        long executionTime = endTime - startTime;
        
//        log.info("线程池批量更新{}条数据，实际更新{}条，失败{}条，耗时：{}ms",
//                count, actualUpdated.get(), errorCount.get(), executionTime);
        
        return new UpdateResult(actualUpdated.get(), executionTime, executionTime / 1000.0, "线程池批量更新");
    }
}
