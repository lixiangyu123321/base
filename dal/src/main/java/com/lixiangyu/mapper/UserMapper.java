package com.lixiangyu.mapper;

import com.lixiangyu.dal.entity.UserDO;
import tk.mybatis.mapper.common.Mapper;
import tk.mybatis.mapper.common.MySqlMapper;

/**
 * 用户Mapper接口
 *
 * @author lixiangyu
 */
public interface UserMapper extends Mapper<UserDO>, MySqlMapper<UserDO> {
}

