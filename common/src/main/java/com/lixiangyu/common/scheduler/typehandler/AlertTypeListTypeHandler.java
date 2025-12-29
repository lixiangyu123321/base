package com.lixiangyu.common.scheduler.typehandler;

import com.lixiangyu.common.scheduler.entity.JobConfig;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 告警类型列表处理器
 * 
 * @author lixiangyu
 */
@MappedTypes({List.class})
@MappedJdbcTypes(JdbcType.VARCHAR)
public class AlertTypeListTypeHandler extends BaseTypeHandler<List<JobConfig.AlertType>> {
    
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<JobConfig.AlertType> parameter, JdbcType jdbcType) throws SQLException {
        if (parameter == null || parameter.isEmpty()) {
            ps.setString(i, null);
        } else {
            ps.setString(i, String.join(",", parameter.stream().map(Enum::name).toArray(String[]::new)));
        }
    }
    
    @Override
    public List<JobConfig.AlertType> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return parseList(value);
    }
    
    @Override
    public List<JobConfig.AlertType> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return parseList(value);
    }
    
    @Override
    public List<JobConfig.AlertType> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return parseList(value);
    }
    
    private List<JobConfig.AlertType> parseList(String value) {
        if (value == null || value.trim().isEmpty()) {
            return new ArrayList<>();
        }
        List<JobConfig.AlertType> list = new ArrayList<>();
        for (String item : value.split(",")) {
            try {
                list.add(JobConfig.AlertType.valueOf(item.trim()));
            } catch (IllegalArgumentException e) {
                // 忽略无效的枚举值
            }
        }
        return list;
    }
}

