package com.example.leaseplatform.sys.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.leaseplatform.sys.entity.SysOperationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作日志表 Mapper。
 */
@Mapper
public interface SysOperationLogMapper extends BaseMapper<SysOperationLog> {
}
