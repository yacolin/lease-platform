package com.example.leaseplatform.mtg.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.common.cache.MultiLevelCache;
import com.example.leaseplatform.mtg.dto.RoomReq;
import com.example.leaseplatform.mtg.dto.RoomVO;
import com.example.leaseplatform.mtg.entity.MtgRoom;
import com.example.leaseplatform.mtg.mapper.MtgRoomMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会议室服务单元测试。
 */
@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private MtgRoomMapper roomMapper;

    @Mock
    private MultiLevelCache cache;

    private RoomService service;

    @BeforeEach
    void setUp() {
        service = new RoomService(roomMapper, cache);
        // MultiLevelCache 为透传 mock：直接调用 loader，使既有断言仍校验真实查询与映射逻辑
        // 用 lenient：只有部分用例会走到缓存读取路径
        lenient().when(cache.getList(anyString(), anyString(), any(), any(), any()))
                .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(4)).get());

    }

    private MtgRoom room(Long id) {
        MtgRoom r = new MtgRoom();
        r.setId(id);
        r.setRoomName("会议室A");
        r.setCapacity(10);
        r.setStatus(1);
        return r;
    }

    @Test
    void publicList_shouldReturnAvailableOnly() {
        when(roomMapper.selectList(any(Wrapper.class))).thenReturn(List.of(room(1L)));

        List<RoomVO> list = service.publicList();

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getRoomName()).isEqualTo("会议室A");
    }

    @Test
    void create_shouldInsert() {
        when(roomMapper.insert(any(MtgRoom.class))).thenAnswer(inv -> {
            ((MtgRoom) inv.getArgument(0)).setId(1L);
            return 1;
        });
        RoomReq req = new RoomReq();
        req.setRoomName("会议室D");
        req.setCapacity(20);

        RoomVO vo = service.create(req);

        assertThat(vo.getId()).isEqualTo(1L);
        assertThat(vo.getRoomName()).isEqualTo("会议室D");
    }

    @Test
    void update_missing_should404() {
        when(roomMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.update(99L, new RoomReq()))
                .isInstanceOf(BizException.class)
                .hasMessage("会议室不存在");
        verify(roomMapper, never()).updateById(any(MtgRoom.class));
    }

    @Test
    void page_shouldReturnPaged() {
        when(roomMapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(inv -> {
            Page<MtgRoom> p = inv.getArgument(0);
            p.setRecords(List.of(room(1L)));
            p.setTotal(1);
            return p;
        });

        var result = service.page(1, 10, null, null);

        assertThat(result.total()).isEqualTo(1);
    }
}
