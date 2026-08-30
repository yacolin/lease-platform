package com.example.leaseplatform.mtg.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.mtg.dto.RoomLevelPriceReq;
import com.example.leaseplatform.mtg.dto.RoomLevelPriceVO;
import com.example.leaseplatform.mtg.entity.MtgRoom;
import com.example.leaseplatform.mtg.entity.MtgRoomLevelPrice;
import com.example.leaseplatform.mtg.mapper.MtgRoomLevelPriceMapper;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会议室等级定价服务单元测试。
 */
@ExtendWith(MockitoExtension.class)
class RoomLevelPriceServiceTest {

    @Mock
    private MtgRoomLevelPriceMapper priceMapper;
    @Mock
    private MtgRoomMapper roomMapper;

    private RoomLevelPriceService service;

    @BeforeEach
    void setUp() {
        service = new RoomLevelPriceService(priceMapper, roomMapper);
    }

    private MtgRoom room() {
        MtgRoom r = new MtgRoom();
        r.setId(1L);
        r.setRoomName("会议室A");
        return r;
    }

    private MtgRoomLevelPrice price() {
        MtgRoomLevelPrice p = new MtgRoomLevelPrice();
        p.setId(1L);
        p.setRoomId(1L);
        p.setLevelCode("VIP");
        p.setOvertimeFee(10000L);
        p.setIsActive(1);
        return p;
    }

    private RoomLevelPriceReq req() {
        RoomLevelPriceReq req = new RoomLevelPriceReq();
        req.setRoomId(1L);
        req.setLevelCode("VIP");
        req.setOvertimeFee(10000L);
        return req;
    }

    @Test
    void create_shouldInsert() {
        when(roomMapper.selectById(1L)).thenReturn(room());
        when(priceMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(priceMapper.insert(any(MtgRoomLevelPrice.class))).thenAnswer(inv -> {
            ((MtgRoomLevelPrice) inv.getArgument(0)).setId(1L);
            return 1;
        });

        RoomLevelPriceVO vo = service.create(req());

        assertThat(vo.getId()).isEqualTo(1L);
        assertThat(vo.getRoomName()).isEqualTo("会议室A");
        assertThat(vo.getLevelCode()).isEqualTo("VIP");
        assertThat(vo.getOvertimeFee()).isEqualTo(10000L);
    }

    @Test
    void create_duplicate_should409() {
        when(roomMapper.selectById(1L)).thenReturn(room());
        when(priceMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> service.create(req()))
                .isInstanceOf(BizException.class)
                .hasMessage("该会议室已配置该等级的定价");
        verify(priceMapper, never()).insert(any(MtgRoomLevelPrice.class));
    }

    @Test
    void create_roomMissing_should404() {
        when(roomMapper.selectById(99L)).thenReturn(null);
        RoomLevelPriceReq req = req();
        req.setRoomId(99L);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BizException.class)
                .hasMessage("会议室不存在");
    }

    @Test
    void page_shouldReturnPaged() {
        when(priceMapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(inv -> {
            Page<MtgRoomLevelPrice> p = inv.getArgument(0);
            p.setRecords(List.of(price()));
            p.setTotal(1);
            return p;
        });
        when(roomMapper.selectBatchIds(List.of(1L))).thenReturn(List.of(room()));

        var result = service.page(1, 10, null);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.list().get(0).getRoomName()).isEqualTo("会议室A");
    }

    @Test
    void update_shouldApply() {
        when(priceMapper.selectById(1L)).thenReturn(price());
        when(roomMapper.selectById(1L)).thenReturn(room());
        when(priceMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        RoomLevelPriceVO vo = service.update(1L, req());

        assertThat(vo.getOvertimeFee()).isEqualTo(10000L);
        verify(priceMapper).updateById(any(MtgRoomLevelPrice.class));
    }

    @Test
    void delete_missing_should404() {
        when(priceMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(BizException.class)
                .hasMessage("定价不存在");
        verify(priceMapper, never()).deleteById(anyLong());
    }
}
