package com.lixiangyu.dal.mapper;

import com.lixiangyu.dal.entity.UserDO;
 import org.apache.ibatis.annotations.Param;
import tk.mybatis.mapper.common.Mapper;
import tk.mybatis.mapper.common.MySqlMapper;

import java.util.Date;
import java.util.List;

/**
 * 用户Mapper接口
 *
 * @author lixiangyu
 */
public interface UserMapper extends Mapper<UserDO>, MySqlMapper<UserDO> {
    
    /**
     * 批量更新用户
     *
     * @param idList 用户ID列表
     * @param updateTime 更新时间
     * @param modifier 修改人
     * @return 更新的记录数
     */
    int batchUpdateUsers(@Param("idList") List<Long> idList, 
                         @Param("updateTime") Date updateTime, 
                         @Param("modifier") String modifier);

    UserDO selectByUsername(String username);
}

