package com.example.leaseplatform.mtg.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.common.TimeUtil;
import com.example.leaseplatform.mtg.dto.RoomReq;
import com.example.leaseplatform.mtg.dto.RoomVO;
import com.example.leaseplatform.mtg.entity.MtgRoom;
import com.example.leaseplatform.mtg.mapper.MtgRoomMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 会议室管理：公开列表（仅可预约）+ 管理端 CRUD（双轨，见 docs/接口规范.md）。
 */
@Service
@RequiredArgsConstructor
public class RoomService {

    private final MtgRoomMapper roomMapper;

    /** 公开列表：仅可预约（status=1） */
    public List<RoomVO> publicList() {
        return roomMapper.selectList(new LambdaQueryWrapper<MtgRoom>()
                        .eq(MtgRoom::getStatus, 1)
                        .orderByAsc(MtgRoom::getId))
                .stream().map(this::toVO).toList();
    }

    /** 管理端分页 */
    public PageResult<RoomVO> page(int page, int size, Integer status, String keyword) {
        Page<MtgRoom> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 1000));
        roomMapper.selectPage(p, new LambdaQueryWrapper<MtgRoom>()
                .eq(status != null, MtgRoom::getStatus, status)
                .like(keyword != null && !keyword.isBlank(), MtgRoom::getRoomName, keyword)
                .orderByAsc(MtgRoom::getId));
        return PageResult.of(p.getTotal(), p.getRecords().stream().map(this::toVO).toList());
    }

    public RoomVO getById(Long id) {
        return toVO(require(id));
    }

    public RoomVO create(RoomReq req) {
        MtgRoom room = new MtgRoom();
        apply(room, req);
        roomMapper.insert(room);
        return toVO(room);
    }

    public RoomVO update(Long id, RoomReq req) {
        MtgRoom room = require(id);
        apply(room, req);
        roomMapper.updateById(room);
        return toVO(room);
    }

    public void delete(Long id) {
        require(id);
        roomMapper.deleteById(id);
    }

    private void apply(MtgRoom room, RoomReq req) {
        room.setRoomName(req.getRoomName());
        room.setCapacity(req.getCapacity());
        room.setEquipment(req.getEquipment());
        room.setSuitableScenes(req.getSuitableScenes());
        room.setImageUrl(req.getImageUrl());
        room.setStatus(req.getStatus() == null ? 1 : req.getStatus());
    }

    private MtgRoom require(Long id) {
        MtgRoom room = roomMapper.selectById(id);
        if (room == null) {
            throw BizException.notFound("会议室不存在");
        }
        return room;
    }

    private RoomVO toVO(MtgRoom room) {
        RoomVO vo = new RoomVO();
        vo.setId(room.getId());
        vo.setRoomName(room.getRoomName());
        vo.setCapacity(room.getCapacity());
        vo.setEquipment(room.getEquipment());
        vo.setSuitableScenes(room.getSuitableScenes());
        vo.setImageUrl(room.getImageUrl());
        vo.setStatus(room.getStatus());
        vo.setCreatedAt(TimeUtil.toEpochMillis(room.getCreatedAt()));
        return vo;
    }
}
