package com.example.leaseplatform.mtg.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.common.TimeUtil;
import com.example.leaseplatform.mtg.dto.RoomLevelPriceReq;
import com.example.leaseplatform.mtg.dto.RoomLevelPriceVO;
import com.example.leaseplatform.mtg.entity.MtgRoom;
import com.example.leaseplatform.mtg.entity.MtgRoomLevelPrice;
import com.example.leaseplatform.mtg.mapper.MtgRoomLevelPriceMapper;
import com.example.leaseplatform.mtg.mapper.MtgRoomMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 会议室等级定价管理（mtg_room_level_prices）：
 * 某会议室对某会员等级的超出费用覆盖（Override），同一 (room_id, level_code) 唯一；
 * 预约计费取值：本表覆盖价 → 无则回落 usr_member_levels.meeting_overtime_fee 默认价。
 */
@Service
@RequiredArgsConstructor
public class RoomLevelPriceService {

    private final MtgRoomLevelPriceMapper priceMapper;
    private final MtgRoomMapper roomMapper;

    /** 管理端分页（可按会议室过滤） */
    public PageResult<RoomLevelPriceVO> page(int page, int size, Long roomId) {
        Page<MtgRoomLevelPrice> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 1000));
        priceMapper.selectPage(p, new LambdaQueryWrapper<MtgRoomLevelPrice>()
                .eq(roomId != null, MtgRoomLevelPrice::getRoomId, roomId)
                .orderByAsc(MtgRoomLevelPrice::getRoomId)
                .orderByAsc(MtgRoomLevelPrice::getLevelCode));
        return PageResult.of(p.getTotal(), withRooms(p.getRecords()));
    }

    /** 某会议室全部定价（管理端详情用） */
    public List<RoomLevelPriceVO> listByRoom(Long roomId) {
        requireRoom(roomId);
        List<MtgRoomLevelPrice> list = priceMapper.selectList(new LambdaQueryWrapper<MtgRoomLevelPrice>()
                .eq(MtgRoomLevelPrice::getRoomId, roomId)
                .orderByAsc(MtgRoomLevelPrice::getLevelCode));
        return withRooms(list);
    }

    public RoomLevelPriceVO getById(Long id) {
        return toVO(require(id));
    }

    public RoomLevelPriceVO create(RoomLevelPriceReq req) {
        requireRoom(req.getRoomId());
        Long exist = priceMapper.selectCount(new LambdaQueryWrapper<MtgRoomLevelPrice>()
                .eq(MtgRoomLevelPrice::getRoomId, req.getRoomId())
                .eq(MtgRoomLevelPrice::getLevelCode, req.getLevelCode()));
        if (exist != null && exist > 0) {
            throw BizException.conflict("该会议室已配置该等级的定价");
        }
        MtgRoomLevelPrice price = new MtgRoomLevelPrice();
        apply(price, req);
        priceMapper.insert(price);
        return toVO(price);
    }

    public RoomLevelPriceVO update(Long id, RoomLevelPriceReq req) {
        MtgRoomLevelPrice price = require(id);
        requireRoom(req.getRoomId());
        // 移动 (room_id, level_code) 时不能与已有行冲突（排除自身）
        Long dup = priceMapper.selectCount(new LambdaQueryWrapper<MtgRoomLevelPrice>()
                .eq(MtgRoomLevelPrice::getRoomId, req.getRoomId())
                .eq(MtgRoomLevelPrice::getLevelCode, req.getLevelCode())
                .ne(MtgRoomLevelPrice::getId, id));
        if (dup != null && dup > 0) {
            throw BizException.conflict("该会议室已配置该等级的定价");
        }
        apply(price, req);
        priceMapper.updateById(price);
        return toVO(price);
    }

    public void delete(Long id) {
        require(id);
        priceMapper.deleteById(id);
    }

    private void apply(MtgRoomLevelPrice price, RoomLevelPriceReq req) {
        price.setRoomId(req.getRoomId());
        price.setLevelCode(req.getLevelCode());
        price.setOvertimeFee(req.getOvertimeFee());
        price.setIsActive(req.getIsActive() == null ? 1 : req.getIsActive());
    }

    private MtgRoomLevelPrice require(Long id) {
        MtgRoomLevelPrice price = priceMapper.selectById(id);
        if (price == null) {
            throw BizException.notFound("定价不存在");
        }
        return price;
    }

    private void requireRoom(Long roomId) {
        if (roomMapper.selectById(roomId) == null) {
            throw BizException.notFound("会议室不存在");
        }
    }

    private List<RoomLevelPriceVO> withRooms(List<MtgRoomLevelPrice> prices) {
        if (prices.isEmpty()) {
            return List.of();
        }
        List<Long> roomIds = prices.stream().map(MtgRoomLevelPrice::getRoomId).distinct().toList();
        Map<Long, MtgRoom> rooms = roomMapper.selectBatchIds(roomIds).stream()
                .collect(Collectors.toMap(MtgRoom::getId, Function.identity()));
        return prices.stream().map(p -> toVO(p, rooms.get(p.getRoomId()))).toList();
    }

    private RoomLevelPriceVO toVO(MtgRoomLevelPrice price) {
        MtgRoom room = roomMapper.selectById(price.getRoomId());
        return toVO(price, room);
    }

    private RoomLevelPriceVO toVO(MtgRoomLevelPrice price, MtgRoom room) {
        RoomLevelPriceVO vo = new RoomLevelPriceVO();
        vo.setId(price.getId());
        vo.setRoomId(price.getRoomId());
        vo.setRoomName(room == null ? null : room.getRoomName());
        vo.setLevelCode(price.getLevelCode());
        vo.setOvertimeFee(price.getOvertimeFee());
        vo.setIsActive(price.getIsActive());
        vo.setCreatedAt(TimeUtil.toEpochMillis(price.getCreatedAt()));
        return vo;
    }
}
