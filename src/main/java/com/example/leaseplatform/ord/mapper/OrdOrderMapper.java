package com.example.leaseplatform.ord.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.leaseplatform.ord.entity.OrdOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单表 Mapper。
 */
@Mapper
public interface OrdOrderMapper extends BaseMapper<OrdOrder> {
}
