package com.example.leaseplatform.sys.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.leaseplatform.sys.entity.SysIdempotency;
import org.apache.ibatis.annotations.Mapper;

/**
 * 幂等记录表 Mapper（1.2）。
 */
@Mapper
public interface SysIdempotencyMapper extends BaseMapper<SysIdempotency> {
}
