package com.example.leaseplatform.ord.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.leaseplatform.ord.entity.OrdOrderItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单明细表 Mapper。
 */
@Mapper
public interface OrdOrderItemMapper extends BaseMapper<OrdOrderItem> {
}
