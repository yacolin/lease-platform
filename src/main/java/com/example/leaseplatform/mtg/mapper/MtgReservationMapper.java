package com.example.leaseplatform.mtg.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.leaseplatform.mtg.entity.MtgReservation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会议室预约表 Mapper。
 */
@Mapper
public interface MtgReservationMapper extends BaseMapper<MtgReservation> {
}
