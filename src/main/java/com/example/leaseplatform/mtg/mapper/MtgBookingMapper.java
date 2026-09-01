package com.example.leaseplatform.mtg.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.leaseplatform.mtg.entity.MtgBooking;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会议室资源占用表 Mapper（1.4）。
 */
@Mapper
public interface MtgBookingMapper extends BaseMapper<MtgBooking> {
}
