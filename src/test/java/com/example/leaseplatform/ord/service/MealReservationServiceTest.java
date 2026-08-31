package com.example.leaseplatform.ord.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.ord.dto.MealReservationCreateReq;
import com.example.leaseplatform.ord.dto.MealReservationVO;
import com.example.leaseplatform.ord.entity.OrdMealReservation;
import com.example.leaseplatform.ord.entity.OrdMealReservationItem;
import com.example.leaseplatform.ord.entity.OrdOrder;
import com.example.leaseplatform.ord.mapper.OrdMealReservationItemMapper;
import com.example.leaseplatform.ord.mapper.OrdMealReservationMapper;
import com.example.leaseplatform.ord.mapper.OrdOrderMapper;
import com.example.leaseplatform.prd.entity.PrdDailyMenu;
import com.example.leaseplatform.prd.entity.PrdProduct;
import com.example.leaseplatform.prd.mapper.PrdDailyMenuMapper;
import com.example.leaseplatform.prd.mapper.PrdProductMapper;
import com.example.leaseplatform.trd.entity.TrdPayment;
import com.example.leaseplatform.trd.service.BalanceService;
import com.example.leaseplatform.trd.service.PaymentService;
import com.example.leaseplatform.usr.entity.UsrUser;
import com.example.leaseplatform.usr.mapper.UsrUserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 正餐预订服务单元测试：规则校验/快照/折扣/支付/取消/备餐流转。
 */
@ExtendWith(MockitoExtension.class)
class MealReservationServiceTest {

    @Mock
    private OrdMealReservationMapper reservationMapper;
    @Mock
    private OrdMealReservationItemMapper itemMapper;
    @Mock
    private OrdOrderMapper orderMapper;
    @Mock
    private PrdProductMapper productMapper;
    @Mock
    private PrdDailyMenuMapper menuMapper;
    @Mock
    private UsrUserMapper userMapper;
    @Mock
    private DiscountCalculator discountCalculator;
    @Mock
    private BalanceService balanceService;
    @Mock
    private PaymentService paymentService;
    @Mock
    private com.example.leaseplatform.trd.service.RefundService refundService;
    @Mock
    private OrderStatusHistoryService statusHistoryService;

    private MealReservationService service;

    @BeforeAll
    static void initMpEntityCache() {
        initTableInfo(OrdMealReservation.class);
        initTableInfo(OrdMealReservationItem.class);
        initTableInfo(OrdOrder.class);
        initTableInfo(PrdDailyMenu.class);
    }

    private static void initTableInfo(Class<?> clazz) {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), clazz);
    }

    @BeforeEach
    void setUp() {
        service = new MealReservationService(reservationMapper, itemMapper, orderMapper,
                productMapper, menuMapper, userMapper, discountCalculator, balanceService,
                paymentService, refundService, statusHistoryService);
    }

    private UsrUser user() {
        UsrUser u = new UsrUser();
        u.setId(1L);
        u.setMemberLevel(0);
        return u;
    }

    private PrdProduct product() {
        PrdProduct p = new PrdProduct();
        p.setId(4L);
        p.setProductName("3荤1素套餐");
        p.setPrice(2000L);
        p.setIsAvailable(1);
        return p;
    }

    /** 窗口内日期（今天+3 恒在可订范围内） */
    private MealReservationCreateReq req(LocalDate date) {
        MealReservationCreateReq req = new MealReservationCreateReq();
        req.setProductId(4L);
        req.setMenuDate(date);
        req.setTimeSlot("午餐");
        req.setQuantity(2);
        req.setDeliveryType(1);
        return req;
    }

    private OrdMealReservation reservation(Long id, int status) {
        OrdMealReservation r = new OrdMealReservation();
        r.setId(id);
        r.setUserId(1L);
        r.setOrderId(200L);
        r.setPayableAmount(3560L);
        r.setStatus(status);
        return r;
    }

    // ==================== 预订 ====================

    @Test
    void create_shouldCreateReservationOrderAndSnapshot() {
        when(userMapper.selectById(1L)).thenReturn(user());
        when(productMapper.selectById(4L)).thenReturn(product());
        when(menuMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(discountCalculator.memberDiscountRate(any())).thenReturn(BigDecimal.ONE);
        when(discountCalculator.rechargeDiscountRate(1L)).thenReturn(new BigDecimal("0.89"));
        when(orderMapper.insert(any(OrdOrder.class))).thenAnswer(inv -> {
            ((OrdOrder) inv.getArgument(0)).setId(200L);
            return 1;
        });
        when(reservationMapper.insert(any(OrdMealReservation.class))).thenAnswer(inv -> {
            ((OrdMealReservation) inv.getArgument(0)).setId(300L);
            return 1;
        });
        when(itemMapper.insert(any(OrdMealReservationItem.class))).thenReturn(1);

        MealReservationVO vo = service.create(1L, req(LocalDate.now().plusDays(3)));

        // 20 × 2 = 40，0.89 折 → 35.60；自取配送费 0
        assertThat(vo.getTotalAmount()).isEqualTo(4000L);
        assertThat(vo.getPayableAmount()).isEqualTo(3560L);
        assertThat(vo.getDeliveryFee()).isEqualTo(0L);
        assertThat(vo.getStatus()).isZero();
        ArgumentCaptor<OrdOrder> orderCaptor = ArgumentCaptor.forClass(OrdOrder.class);
        verify(orderMapper).insert(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getOrderType()).isEqualTo(2);
        assertThat(orderCaptor.getValue().getPayableAmount()).isEqualTo(3560L);
        assertThat(orderCaptor.getValue().getReservationDate()).isEqualTo(LocalDate.now().plusDays(3));
    }

    @Test
    void create_outsideWindow_should400() {
        // 今天不在可订窗口（最早明天/后天）
        assertThatThrownBy(() -> service.create(1L, req(LocalDate.now())))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("可预订日期为");
        verify(orderMapper, never()).insert(any(OrdOrder.class));
    }

    @Test
    void create_noMenu_should400() {
        when(userMapper.selectById(1L)).thenReturn(user());
        when(productMapper.selectById(4L)).thenReturn(product());
        when(menuMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        assertThatThrownBy(() -> service.create(1L, req(LocalDate.now().plusDays(3))))
                .isInstanceOf(BizException.class)
                .hasMessage("该日期暂无此套餐菜单");
    }

    @Test
    void create_surroundingWithoutAddress_should400() {
        when(userMapper.selectById(1L)).thenReturn(user());
        when(productMapper.selectById(4L)).thenReturn(product());
        when(menuMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        MealReservationCreateReq req = req(LocalDate.now().plusDays(3));
        req.setDeliveryType(3); // 周边配送但无地址

        assertThatThrownBy(() -> service.create(1L, req))
                .isInstanceOf(BizException.class)
                .hasMessage("周边配送请填写配送地址");
    }

    // ==================== 支付 / 取消 ====================

    @Test
    void pay_shouldDebitAndSyncOrder() {
        OrdMealReservation r = reservation(300L, 0);
        when(reservationMapper.selectById(300L)).thenReturn(r);
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(orderMapper.selectById(200L)).thenReturn(new OrdOrder());
        when(reservationMapper.update(any(), any(Wrapper.class))).thenReturn(1); // 乐观锁定 0→1
        TrdPayment payment = new TrdPayment();
        payment.setId(900L);
        when(paymentService.create(any(), anyInt(), any(), anyLong(), anyInt(), anyInt(), any()))
                .thenReturn(payment);

        MealReservationVO vo = service.pay(1L, 300L);

        verify(balanceService).debit(1L, 3560L, 200L, "正餐预订");
        // 1.2：创建并结算支付单 + 状态历史
        verify(paymentService).create(eq(1L), eq(PaymentService.BIZ_MEAL_RESERVATION), eq(300L),
                eq(3560L), eq(PaymentService.METHOD_BALANCE), eq(PaymentService.CHANNEL_BALANCE), any());
        verify(paymentService).settle(900L, null);
        verify(statusHistoryService).record(eq(OrderStatusHistoryService.BIZ_MEAL_RESERVATION),
                eq(300L), eq(0), eq(1), eq(1L), eq(OrderStatusHistoryService.OPERATOR_USER), any());
        assertThat(vo.getStatus()).isEqualTo(1);
        verify(orderMapper).updateById(any(OrdOrder.class)); // 同步关联订单
    }

    @Test
    void pay_concurrentDuplicate_shouldConflictAndNotSettle() {
        // 并发：先扣款、乐观更新失败 → 抛"预订已处理"，本事务回滚扣款（生产由事务保证）
        OrdMealReservation r = reservation(300L, 0);
        when(reservationMapper.selectById(300L)).thenReturn(r);
        when(reservationMapper.update(any(), any(Wrapper.class))).thenReturn(0); // 并发

        assertThatThrownBy(() -> service.pay(1L, 300L))
                .isInstanceOf(BizException.class)
                .hasMessage("预订已处理");
        verify(balanceService).debit(1L, 3560L, 200L, "正餐预订");
        verify(paymentService, never()).settle(any(), any());
    }

    @Test
    void cancel_paidNoPayment_shouldLegacyRefundAndSync() {
        // 1.2 之前的历史预订无支付单：回退为直接余额入账
        OrdMealReservation r = reservation(300L, 1);
        when(reservationMapper.selectById(300L)).thenReturn(r);
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(orderMapper.selectById(200L)).thenReturn(new OrdOrder());
        when(paymentService.getByBiz(anyInt(), any())).thenReturn(null);

        MealReservationVO vo = service.cancel(1L, 300L, "行程变化");

        verify(balanceService).credit(1L, 3560L, 0L,
                BalanceService.TX_REFUND, 200L, null, "预订取消退款");
        assertThat(vo.getStatus()).isEqualTo(4);
        assertThat(vo.getCancelReason()).isEqualTo("行程变化");
    }

    @Test
    void cancel_paid_shouldRefundViaRefundService() {
        OrdMealReservation r = reservation(300L, 1);
        when(reservationMapper.selectById(300L)).thenReturn(r);
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(orderMapper.selectById(200L)).thenReturn(new OrdOrder());
        TrdPayment payment = new TrdPayment();
        payment.setId(900L);
        when(paymentService.getByBiz(PaymentService.BIZ_MEAL_RESERVATION, 300L)).thenReturn(payment);
        when(refundService.refundToBalance(any(), any(), anyLong(), any(), any(), any()))
                .thenReturn(new com.example.leaseplatform.trd.entity.TrdRefund());

        MealReservationVO vo = service.cancel(1L, 300L, "行程变化");

        verify(refundService).refundToBalance(1L, 900L, 3560L, "预订取消退款",
                "MEAL_CANCEL_REFUND:300", 200L);
        verify(balanceService, never()).credit(any(), anyLong(), anyLong(), anyInt(), any(), any(), any());
        assertThat(vo.getStatus()).isEqualTo(4);
    }

    @Test
    void cancel_making_shouldConflict() {
        when(reservationMapper.selectById(300L)).thenReturn(reservation(300L, 2));

        assertThatThrownBy(() -> service.cancel(1L, 300L, null))
                .isInstanceOf(BizException.class)
                .hasMessage("预订备餐中，暂不可取消");
    }

    // ==================== 商家备餐流转 ====================

    @Test
    void adminUpdateStatus_shouldAdvanceAndSync() {
        OrdMealReservation r = reservation(300L, 1);
        when(reservationMapper.selectById(300L)).thenReturn(r);
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(orderMapper.selectById(200L)).thenReturn(new OrdOrder());

        MealReservationVO vo = service.adminUpdateStatus(300L, 2, 9L);

        assertThat(vo.getStatus()).isEqualTo(2);
        verify(orderMapper).updateById(any(OrdOrder.class));
        verify(statusHistoryService).record(eq(OrderStatusHistoryService.BIZ_MEAL_RESERVATION),
                eq(300L), eq(1), eq(2), eq(9L), eq(OrderStatusHistoryService.OPERATOR_ADMIN), any());
    }

    @Test
    void adminUpdateStatus_refundNoPayment_shouldLegacyRefund() {
        OrdMealReservation r = reservation(300L, 2);
        when(reservationMapper.selectById(300L)).thenReturn(r);
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(paymentService.getByBiz(anyInt(), any())).thenReturn(null);

        MealReservationVO vo = service.adminUpdateStatus(300L, 5, 9L);

        assertThat(vo.getStatus()).isEqualTo(5);
        verify(balanceService).credit(any(), anyLong(), anyLong(), anyInt(), any(), any(), any());
    }

    @Test
    void adminUpdateStatus_refund_shouldRefundViaRefundService() {
        OrdMealReservation r = reservation(300L, 2);
        when(reservationMapper.selectById(300L)).thenReturn(r);
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(orderMapper.selectById(200L)).thenReturn(new OrdOrder());
        TrdPayment payment = new TrdPayment();
        payment.setId(900L);
        when(paymentService.getByBiz(PaymentService.BIZ_MEAL_RESERVATION, 300L)).thenReturn(payment);
        when(refundService.refundToBalance(any(), any(), anyLong(), any(), any(), any()))
                .thenReturn(new com.example.leaseplatform.trd.entity.TrdRefund());

        MealReservationVO vo = service.adminUpdateStatus(300L, 5, 9L);

        verify(refundService).refundToBalance(1L, 900L, 3560L, "商家退款",
                "MEAL_ADMIN_REFUND:300", 200L);
        assertThat(vo.getStatus()).isEqualTo(5);
    }

    @Test
    void adminUpdateStatus_illegal_shouldConflict() {
        when(reservationMapper.selectById(300L)).thenReturn(reservation(300L, 0));

        assertThatThrownBy(() -> service.adminUpdateStatus(300L, 2, 9L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("非法的状态流转");
    }

    // ==================== 查询 ====================

    @Test
    void myReservations_shouldReturnPaged() {
        when(reservationMapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(inv -> {
            Page<OrdMealReservation> p = inv.getArgument(0);
            p.setRecords(List.of(reservation(300L, 1)));
            p.setTotal(1);
            return p;
        });
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        PageResult<MealReservationVO> result = service.myReservations(1L, 1, 10, null);

        assertThat(result.total()).isEqualTo(1);
    }
}
