package com.example.leaseplatform.mtg.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.common.TimeUtil;
import com.example.leaseplatform.common.cache.CacheSpec;
import com.example.leaseplatform.common.cache.MultiLevelCache;
import com.example.leaseplatform.mtg.dto.RoomReq;
import com.example.leaseplatform.mtg.dto.RoomVO;
import com.example.leaseplatform.mtg.entity.MtgRoom;
import com.example.leaseplatform.mtg.mapper.MtgRoomMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 会议室管理：公开列表（仅可预约）+ 管理端 CRUD（双轨，见 docs/接口规范.md）。
 */
@Service
@RequiredArgsConstructor
public class RoomService {

    private final MtgRoomMapper roomMapper;
    private final MultiLevelCache cache;

    /**
     * 公开列表：仅可预约（status=1）。
     *
     * <p>整表缓存（L1 + L2）：会议室是参照数据，整表只有几行；
     * 原实现每次请求都走一次表扫描（mtg_rooms 原本只有主键，且 status/is_deleted 无索引）。
     */
    public List<RoomVO> publicList() {
        return cache.getList(CacheSpec.ROOM_LIST, MultiLevelCache.l2Key(CacheSpec.ROOM_LIST),
                RoomVO.class, CacheSpec.L2_TTL,
                () -> roomMapper.selectList(new LambdaQueryWrapper<MtgRoom>()
                                .eq(MtgRoom::getStatus, 1)
                                .orderByAsc(MtgRoom::getId))
                        .stream().map(this::toVO).toList());
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
        evictPublicList();
        return toVO(room);
    }

    @Transactional
    public RoomVO update(Long id, RoomReq req) {
        MtgRoom room = require(id);
        apply(room, req);
        roomMapper.updateById(room);
        evictPublicList();
        return toVO(room);
    }

    @Transactional
    public void delete(Long id) {
        require(id);
        roomMapper.deleteById(id);
        evictPublicList();
    }

    /** 会议室变更后失效公开列表缓存（提交后生效） */
    private void evictPublicList() {
        cache.evictAfterCommit(CacheSpec.ROOM_LIST, MultiLevelCache.l2Key(CacheSpec.ROOM_LIST));
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
