package com.example.leaseplatform.ord.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.ord.dto.OrderStatusHistoryVO;
import com.example.leaseplatform.ord.entity.OrdMealReservation;
import com.example.leaseplatform.ord.entity.OrdOrder;
import com.example.leaseplatform.ord.entity.OrdOrderStatusHistory;
import com.example.leaseplatform.ord.mapper.OrdMealReservationMapper;
import com.example.leaseplatform.ord.mapper.OrdOrderMapper;
import com.example.leaseplatform.ord.mapper.OrdOrderStatusHistoryMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单状态历史服务单元测试（1.2）：留痕 / 归属校验（我的 vs 管理端）。
 */
@ExtendWith(MockitoExtension.class)
class OrderStatusHistoryServiceTest {

    @Mock
    private OrdOrderStatusHistoryMapper historyMapper;
    @Mock
    private OrdOrderMapper orderMapper;
    @Mock
    private OrdMealReservationMapper reservationMapper;

    private OrderStatusHistoryService service;

    @BeforeAll
    static void initMpEntityCache() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                OrdOrderStatusHistory.class);
    }

    @BeforeEach
    void setUp() {
        service = new OrderStatusHistoryService(historyMapper, orderMapper, reservationMapper);
    }

    @Test
    void record_shouldInsertHistory() {
        when(historyMapper.insert(any(OrdOrderStatusHistory.class))).thenReturn(1);

        service.record(OrderStatusHistoryService.BIZ_COFFEE_ORDER, 100L, 0, 1,
                1L, OrderStatusHistoryService.OPERATOR_USER, "余额支付");

        ArgumentCaptor<OrdOrderStatusHistory> captor = ArgumentCaptor.forClass(OrdOrderStatusHistory.class);
        verify(historyMapper).insert(captor.capture());
        assertThat(captor.getValue().getFromStatus()).isZero();
        assertThat(captor.getValue().getToStatus()).isEqualTo(1);
        assertThat(captor.getValue().getOperatorId()).isEqualTo(1L);
    }

    @Test
    void mine_coffeeOrderOwned_shouldReturnHistory() {
        OrdOrder order = new OrdOrder();
        order.setId(100L);
        order.setUserId(1L);
        when(orderMapper.selectById(100L)).thenReturn(order);
        OrdOrderStatusHistory h = new OrdOrderStatusHistory();
        h.setToStatus(1);
        when(historyMapper.selectList(any(Wrapper.class))).thenReturn(List.of(h));

        List<OrderStatusHistoryVO> list = service.mine(1L,
                OrderStatusHistoryService.BIZ_COFFEE_ORDER, 100L);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getToStatus()).isEqualTo(1);
    }

    @Test
    void mine_notOwnOrder_should404() {
        OrdOrder order = new OrdOrder();
        order.setId(100L);
        order.setUserId(99L);
        when(orderMapper.selectById(100L)).thenReturn(order);

        assertThatThrownBy(() -> service.mine(1L, OrderStatusHistoryService.BIZ_COFFEE_ORDER, 100L))
                .isInstanceOf(BizException.class)
                .hasMessage("订单不存在");
    }

    @Test
    void mine_mealReservationOwned_shouldReturnHistory() {
        OrdMealReservation r = new OrdMealReservation();
        r.setId(300L);
        r.setUserId(1L);
        when(reservationMapper.selectById(300L)).thenReturn(r);
        when(historyMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        assertThat(service.mine(1L, OrderStatusHistoryService.BIZ_MEAL_RESERVATION, 300L)).isEmpty();
    }

    @Test
    void mine_invalidBizType_should400() {
        assertThatThrownBy(() -> service.mine(1L, 99, 100L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不支持的业务类型");
    }

    @Test
    void admin_shouldReturnHistoryWithoutOwnership() {
        when(historyMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        assertThat(service.admin(OrderStatusHistoryService.BIZ_COFFEE_ORDER, 100L)).isEmpty();
        verify(historyMapper).selectList(any(Wrapper.class));
    }
}
