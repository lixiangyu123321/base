package com.lixiangyu.common.scheduler.typehandler;

import com.alibaba.fastjson.JSON;
import com.lixiangyu.common.scheduler.entity.JobConfig;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 告警接收人处理器
 * 用于处理AlterReceivers于json之间的转换
 * @author lixiangyu
 */
@MappedTypes({JobConfig.AlertReceivers.class})
@MappedJdbcTypes(JdbcType.VARCHAR)
public class AlertReceiversTypeHandler extends BaseTypeHandler<JobConfig.AlertReceivers> {
    
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, JobConfig.AlertReceivers parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, JSON.toJSONString(parameter));
    }
    
    @Override
    public JobConfig.AlertReceivers getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String json = rs.getString(columnName);
        return parseJson(json);
    }
    
    @Override
    public JobConfig.AlertReceivers getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String json = rs.getString(columnIndex);
        return parseJson(json);
    }
    
    @Override
    public JobConfig.AlertReceivers getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String json = cs.getString(columnIndex);
        return parseJson(json);
    }
    
    private JobConfig.AlertReceivers parseJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        return JSON.parseObject(json, JobConfig.AlertReceivers.class);
    }
}

