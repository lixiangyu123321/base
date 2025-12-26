package com.lixiangyu.dal.mapper;

import com.lixiangyu.dal.entity.EvaluatingDO;
import tk.mybatis.mapper.common.Mapper;
import tk.mybatis.mapper.common.MySqlMapper;

import java.util.List;

/**
 * 评测记录Mapper接口
 *
 * @author lixiangyu
 */
public interface EvaluatingMapper extends Mapper<EvaluatingDO>, MySqlMapper<EvaluatingDO> {


    /**
     * 批量更新
     *
     * @param dosToUpdate 需要更新的记录列表
     * @return 返回更新的数量
     */
    int batchUpdate(List<EvaluatingDO> dosToUpdate);

    /**
     * 批量插入（自定义方法，避免使用 TK MyBatis 的 insertList）
     *
     * @param evaluatingList 需要插入的记录列表
     * @return 返回插入的数量
     */
    int batchInsert(List<EvaluatingDO> evaluatingList);
}

