package com.example.leaseplatform.mtg.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.mtg.dto.MeetingReservationCreateReq;
import com.example.leaseplatform.mtg.dto.MeetingReservationVO;
import com.example.leaseplatform.mtg.dto.MeetingRescheduleReq;
import com.example.leaseplatform.mtg.entity.MtgBooking;
import com.example.leaseplatform.mtg.entity.MtgReservation;
import com.example.leaseplatform.mtg.entity.MtgRoom;
import com.example.leaseplatform.mtg.entity.MtgRoomLevelPrice;
import com.example.leaseplatform.mtg.mapper.MtgBookingMapper;
import com.example.leaseplatform.mtg.mapper.MtgReservationMapper;
import com.example.leaseplatform.mtg.mapper.MtgRoomLevelPriceMapper;
import com.example.leaseplatform.mtg.mapper.MtgRoomMapper;
import com.example.leaseplatform.ord.entity.OrdOrder;
import com.example.leaseplatform.ord.mapper.OrdOrderMapper;
import com.example.leaseplatform.ord.service.OrderService;
import com.example.leaseplatform.ord.service.OrderStatusHistoryService;
import com.example.leaseplatform.trd.entity.TrdPayment;
import com.example.leaseplatform.trd.entity.TrdRefund;
import com.example.leaseplatform.trd.service.AccountService;
import com.example.leaseplatform.trd.service.PaymentService;
import com.example.leaseplatform.trd.service.RefundService;
import com.example.leaseplatform.usr.entity.UsrEnterprise;
import com.example.leaseplatform.usr.entity.UsrMemberLevel;
import com.example.leaseplatform.usr.entity.UsrUser;
import com.example.leaseplatform.usr.mapper.UsrEnterpriseMapper;
import com.example.leaseplatform.usr.mapper.UsrMemberLevelMapper;
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
import java.time.LocalDateTime;
import java.time.LocalTime;
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
 * 会议室预约服务单元测试（1.4 会议室资源化）：
 * 占用冲突（mtg_bookings + 行锁）/ 免费时长与超时计费 / 预约订单化（订单+支付单）/
 * 生命周期（待确认→已确认→使用中→完成 / 取消）/ 改期 / 定时过期清理。
 */
@ExtendWith(MockitoExtension.class)
class MeetingReservationServiceTest {

    @Mock
    private MtgReservationMapper reservationMapper;
    @Mock
    private MtgRoomMapper roomMapper;
    @Mock
    private MtgRoomLevelPriceMapper roomLevelPriceMapper;
    @Mock
    private MtgBookingMapper bookingMapper;
    @Mock
    private UsrUserMapper userMapper;
    @Mock
    private UsrEnterpriseMapper enterpriseMapper;
    @Mock
    private UsrMemberLevelMapper memberLevelMapper;
    @Mock
    private AccountService accountService;
    @Mock
    private PaymentService paymentService;
    @Mock
    private RefundService refundService;
    @Mock
    private OrdOrderMapper orderMapper;
    @Mock
    private OrderStatusHistoryService statusHistoryService;

    private MeetingReservationService service;

    @BeforeAll
    static void initMpEntityCache() {
        initTableInfo(MtgReservation.class);
        initTableInfo(MtgRoom.class);
        initTableInfo(MtgRoomLevelPrice.class);
        initTableInfo(MtgBooking.class);
        initTableInfo(OrdOrder.class);
        initTableInfo(UsrEnterprise.class);
        initTableInfo(UsrMemberLevel.class);
    }

    private static void initTableInfo(Class<?> clazz) {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), clazz);
    }

    @BeforeEach
    void setUp() {
        service = new MeetingReservationService(reservationMapper, roomMapper, roomLevelPriceMapper,
                bookingMapper, userMapper, enterpriseMapper, memberLevelMapper, accountService,
                paymentService, refundService, orderMapper, statusHistoryService);
    }

    private MtgRoom room() {
        MtgRoom r = new MtgRoom();
        r.setId(1L);
        r.setRoomName("会议室A");
        r.setStatus(1);
        return r;
    }

    private UsrUser user(Long enterpriseId) {
        UsrUser u = new UsrUser();
        u.setId(1L);
        u.setEnterpriseId(enterpriseId);
        return u;
    }

    private UsrEnterprise vipEnterprise() {
        UsrEnterprise e = new UsrEnterprise();
        e.setId(5L);
        e.setMemberLevel(2); // VIP
        e.setMemberExpireAt(LocalDateTime.now().plusDays(30));
        return e;
    }

    private UsrMemberLevel vipLevel() {
        UsrMemberLevel l = new UsrMemberLevel();
        l.setLevelCode("VIP");
        l.setMonthlyMeetingHours(4);
        l.setMeetingOvertimeFee(8000L);
        l.setStatus(1);
        return l;
    }

    private UsrMemberLevel basicLevel() {
        UsrMemberLevel l = new UsrMemberLevel();
        l.setLevelCode("BASIC");
        l.setMonthlyMeetingHours(0);
        l.setMeetingOvertimeFee(8000L);
        l.setStatus(1);
        return l;
    }

    private MeetingReservationCreateReq req(LocalTime start, LocalTime end) {
        MeetingReservationCreateReq req = new MeetingReservationCreateReq();
        req.setRoomId(1L);
        req.setReservationDate(LocalDate.now().plusDays(2));
        req.setStartTime(start);
        req.setEndTime(end);
        req.setMeetingTopic("周会");
        return req;
    }

    private MtgBooking booking(Long roomId, LocalTime start, LocalTime end) {
        MtgBooking b = new MtgBooking();
        b.setRoomId(roomId);
        b.setReservationId(99L);
        b.setStartAt(LocalDateTime.of(LocalDate.now().plusDays(2), start));
        b.setEndAt(LocalDateTime.of(LocalDate.now().plusDays(2), end));
        b.setStatus(MtgBooking.STATUS_OCCUPIED);
        return b;
    }

    private MtgReservation reservation(Long id, int status, long fee, Long orderId) {
        MtgReservation r = new MtgReservation();
        r.setId(id);
        r.setRoomId(1L);
        r.setUserId(1L);
        r.setEnterpriseId(0L);
        r.setOrderId(orderId);
        r.setStatus(status);
        r.setFeeAmount(fee);
        r.setDurationHours(new BigDecimal("2.0"));
        return r;
    }

    /** 创建基础 stub：用户/会议室/行锁/无占用冲突/免费时长查询为空 */
    private void stubCreateBase(UsrUser user, UsrMemberLevel level) {
        when(userMapper.selectById(1L)).thenReturn(user);
        when(roomMapper.selectById(1L)).thenReturn(room());
        when(roomMapper.selectOne(any(Wrapper.class))).thenReturn(room()); // 行锁
        when(memberLevelMapper.selectOne(any(Wrapper.class))).thenReturn(level);
        when(reservationMapper.insert(any(MtgReservation.class))).thenAnswer(inv -> {
            ((MtgReservation) inv.getArgument(0)).setId(100L);
            return 1;
        });
        when(bookingMapper.insert(any(MtgBooking.class))).thenReturn(1);
    }

    // ==================== 预约 / 冲突 / 计费 / 订单化 ====================

    @Test
    void create_paid_shouldCreateOrderPaymentAndBookingWithoutDebit() {
        when(bookingMapper.selectList(any(Wrapper.class))).thenReturn(List.of()); // 无占用冲突
        stubCreateBase(user(null), basicLevel()); // 个人用户无免费时长
        when(orderMapper.insert(any(OrdOrder.class))).thenReturn(1);

        MeetingReservationVO vo = service.create(1L, req(LocalTime.of(9, 0), LocalTime.of(11, 0)));

        // 2 小时 × 80 = 160，无免费时长 → 待确认
        assertThat(vo.getFeeAmount()).isEqualTo(16000L);
        assertThat(vo.getOvertimeUnitPrice()).isEqualTo(8000L);
        assertThat(vo.getIsFree()).isZero();
        assertThat(vo.getStatus()).isZero();
        // 1.4：创建时不扣款；占用记录 + 关联订单 + 支付单
        verify(accountService, never()).debit(any(), anyLong(), any(), any());
        verify(bookingMapper).insert(any(MtgBooking.class));
        verify(orderMapper).insert(any(OrdOrder.class));
        verify(paymentService).create(eq(1L), eq(PaymentService.BIZ_MEETING), eq(100L),
                eq(16000L), eq(PaymentService.METHOD_BALANCE), eq(PaymentService.CHANNEL_BALANCE), any());
        // 1.5.3 预授权：创建时冻结费用
        verify(accountService).freeze(eq(1L), eq(16000L), any(), eq("会议室预约预授权"));
        verify(statusHistoryService).record(eq(OrderStatusHistoryService.BIZ_MEETING_ORDER),
                any(), eq(null), eq(OrderService.STATUS_PENDING), eq(1L), anyInt(), any());
    }

    @Test
    void create_free_shouldConfirmWithoutOrder() {
        when(bookingMapper.selectList(any(Wrapper.class))).thenReturn(List.of()); // 无占用冲突
        stubCreateBase(user(5L), vipLevel()); // 企业 VIP 4h/月
        when(enterpriseMapper.selectById(5L)).thenReturn(vipEnterprise());
        when(reservationMapper.selectList(any(Wrapper.class))).thenReturn(List.of()); // 本月未用

        MeetingReservationVO vo = service.create(1L, req(LocalTime.of(9, 0), LocalTime.of(11, 0)));

        assertThat(vo.getFeeAmount()).isZero();
        assertThat(vo.getIsFree()).isEqualTo(1);
        assertThat(vo.getStatus()).isEqualTo(1); // 免费直接已确认
        assertThat(vo.getFreeHoursDeducted()).isEqualByComparingTo("2.0");
        verify(accountService, never()).debit(any(), anyLong(), any(), any());
        verify(orderMapper, never()).insert(any(OrdOrder.class)); // 免费无交易
        verify(paymentService, never()).create(any(), anyInt(), any(), anyLong(), anyInt(), anyInt(), any());
    }

    @Test
    void create_partiallyFree_shouldChargeOvertime() {
        when(bookingMapper.selectList(any(Wrapper.class))).thenReturn(List.of()); // 无占用冲突
        stubCreateBase(user(5L), vipLevel());
        when(enterpriseMapper.selectById(5L)).thenReturn(vipEnterprise());
        MtgReservation used = new MtgReservation();
        used.setIsFree(1);
        used.setStatus(1);
        used.setDurationHours(new BigDecimal("3.0"));
        when(reservationMapper.selectList(any(Wrapper.class))).thenReturn(List.of(used));
        when(orderMapper.insert(any(OrdOrder.class))).thenReturn(1);

        MeetingReservationVO vo = service.create(1L, req(LocalTime.of(11, 0), LocalTime.of(13, 0)));

        assertThat(vo.getFeeAmount()).isEqualTo(8000L); // 超时 1h × 80
        assertThat(vo.getIsFree()).isZero();
        assertThat(vo.getStatus()).isZero();
        assertThat(vo.getFreeHoursDeducted()).isEqualByComparingTo("1.0");
    }

    @Test
    void create_conflict_should409() {
        when(userMapper.selectById(1L)).thenReturn(user(null));
        when(roomMapper.selectById(1L)).thenReturn(room());
        when(roomMapper.selectOne(any(Wrapper.class))).thenReturn(room()); // 行锁
        when(bookingMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(booking(1L, LocalTime.of(9, 30), LocalTime.of(10, 30))));

        assertThatThrownBy(() -> service.create(1L, req(LocalTime.of(10, 0), LocalTime.of(11, 0))))
                .isInstanceOf(BizException.class)
                .hasMessage("该时段已被预约");
        verify(reservationMapper, never()).insert(any(MtgReservation.class));
        verify(bookingMapper, never()).insert(any(MtgBooking.class));
    }

    @Test
    void create_adjacentTime_shouldNotConflict() {
        // 占用过滤在 SQL 层：相邻时段（10:00 结束 vs 10:00 开始）被查询排除 → mock 返回空
        stubCreateBase(user(null), basicLevel());
        when(bookingMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of()); // SQL 已按重叠条件过滤，无占用返回

        MeetingReservationVO vo = service.create(1L, req(LocalTime.of(10, 0), LocalTime.of(12, 0)));

        assertThat(vo.getId()).isEqualTo(100L);
    }

    @Test
    void create_today_should400() {
        when(userMapper.selectById(1L)).thenReturn(user(null));
        when(roomMapper.selectById(1L)).thenReturn(room());
        MeetingReservationCreateReq req = req(LocalTime.of(9, 0), LocalTime.of(10, 0));
        req.setReservationDate(LocalDate.now());

        assertThatThrownBy(() -> service.create(1L, req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("可预约日期为");
    }

    // ==================== 支付 / 取消 / 状态推进 / 改期 / 过期 ====================

    @Test
    void pay_shouldDebitAndSettlePaymentAndConfirm() {
        MtgReservation r = reservation(100L, 0, 16000L, 200L);
        when(reservationMapper.selectById(100L)).thenReturn(r);
        when(roomMapper.selectById(1L)).thenReturn(room());
        when(reservationMapper.update(any(), any(Wrapper.class))).thenReturn(1); // 乐观锁定 0→1
        when(orderMapper.update(any(), any(Wrapper.class))).thenReturn(1);       // 订单 0→1
        TrdPayment payment = new TrdPayment();
        payment.setId(900L);
        when(paymentService.getByBiz(PaymentService.BIZ_MEETING, 100L)).thenReturn(payment);
        when(paymentService.settle(900L, null)).thenReturn(true);

        MeetingReservationVO vo = service.pay(1L, 100L);

        verify(accountService).unfreeze(1L, 16000L, 200L, "会议室预约支付解冻");
        verify(accountService).debit(1L, 16000L, 200L, "会议室预约");
        verify(paymentService).settle(900L, null);
        verify(statusHistoryService).record(eq(OrderStatusHistoryService.BIZ_MEETING_ORDER),
                eq(200L), eq(OrderService.STATUS_PENDING), eq(OrderService.STATUS_PICKUP), eq(1L), anyInt(), any());
        assertThat(vo.getStatus()).isEqualTo(1);
    }

    @Test
    void pay_insufficientBalance_shouldNotWrite() {
        MtgReservation r = reservation(100L, 0, 16000L, 200L);
        when(reservationMapper.selectById(100L)).thenReturn(r);
        org.mockito.Mockito.doThrow(BizException.conflict("余额不足"))
                .when(accountService).debit(any(), anyLong(), any(), any());

        assertThatThrownBy(() -> service.pay(1L, 100L))
                .isInstanceOf(BizException.class)
                .hasMessage("余额不足");
        verify(reservationMapper, never()).update(any(), any(Wrapper.class));
        verify(paymentService, never()).settle(any(), any());
    }

    @Test
    void pay_notPending_shouldConflict() {
        when(reservationMapper.selectById(100L)).thenReturn(reservation(100L, 1, 16000L, 200L));

        assertThatThrownBy(() -> service.pay(1L, 100L))
                .isInstanceOf(BizException.class)
                .hasMessage("预约已处理");
        verify(accountService, never()).debit(any(), anyLong(), any(), any());
    }

    @Test
    void cancel_paid_shouldRefundViaRefundServiceAndReleaseBooking() {
        MtgReservation r = reservation(100L, 1, 16000L, 200L);
        when(reservationMapper.selectById(100L)).thenReturn(r);
        when(roomMapper.selectById(1L)).thenReturn(room());
        TrdPayment payment = new TrdPayment();
        payment.setId(900L);
        when(paymentService.getByBiz(PaymentService.BIZ_MEETING, 100L)).thenReturn(payment);
        when(refundService.refundToBalance(any(), any(), anyLong(), any(), any(), any()))
                .thenReturn(new TrdRefund());

        MeetingReservationVO vo = service.cancel(1L, 100L, "改期");

        verify(refundService).refundToBalance(1L, 900L, 16000L, "会议室预约取消退款",
                "MEETING_CANCEL_REFUND:100", 200L);
        verify(orderMapper).update(any(), any(Wrapper.class)); // 关联订单取消
        verify(bookingMapper).update(any(), any(Wrapper.class)); // 释放占用
        assertThat(vo.getStatus()).isEqualTo(4);
        assertThat(vo.getCancelReason()).isEqualTo("改期");
    }

    @Test
    void cancel_pending_shouldUnfreezeWithoutRefund() {
        // 1.5.3 预授权：待确认取消 → 解冻，未扣款不退款
        MtgReservation r = reservation(100L, 0, 16000L, 200L);
        when(reservationMapper.selectById(100L)).thenReturn(r);
        when(roomMapper.selectById(1L)).thenReturn(room());

        MeetingReservationVO vo = service.cancel(1L, 100L, "改期");

        verify(accountService).unfreeze(1L, 16000L, 200L, "会议室预约取消解冻");
        verify(refundService, never()).refundToBalance(any(), any(), anyLong(), any(), any(), any());
        verify(orderMapper).update(any(), any(Wrapper.class)); // 关联订单取消
        verify(bookingMapper).update(any(), any(Wrapper.class)); // 释放占用
        assertThat(vo.getStatus()).isEqualTo(4);
    }

    @Test
    void cancel_free_shouldNotRefund() {
        MtgReservation r = reservation(100L, 1, 0L, null);
        when(reservationMapper.selectById(100L)).thenReturn(r);
        when(roomMapper.selectById(1L)).thenReturn(room());

        service.cancel(1L, 100L, null);

        verify(refundService, never()).refundToBalance(any(), any(), anyLong(), any(), any(), any());
        verify(accountService, never()).credit(any(), anyLong(), anyLong(), anyInt(), any(), any(), any());
        verify(bookingMapper).update(any(), any(Wrapper.class));
    }

    @Test
    void adminUpdateStatus_confirmToInUse() {
        MtgReservation r = reservation(100L, 1, 0L, null);
        when(reservationMapper.selectById(100L)).thenReturn(r);
        when(roomMapper.selectById(1L)).thenReturn(room());

        MeetingReservationVO vo = service.adminUpdateStatus(100L, 2, 9L);

        assertThat(vo.getStatus()).isEqualTo(2);
        verify(bookingMapper, never()).update(any(), any(Wrapper.class)); // 使用中不释放占用
    }

    @Test
    void adminUpdateStatus_inUseToCompleted_shouldCompleteOrderAndRelease() {
        MtgReservation r = reservation(100L, 2, 16000L, 200L);
        when(reservationMapper.selectById(100L)).thenReturn(r);
        when(roomMapper.selectById(1L)).thenReturn(room());

        MeetingReservationVO vo = service.adminUpdateStatus(100L, 3, 9L);

        assertThat(vo.getStatus()).isEqualTo(3);
        verify(orderMapper).update(any(), any(Wrapper.class)); // 关联订单完成
        verify(bookingMapper).update(any(), any(Wrapper.class)); // 释放占用
        verify(statusHistoryService).record(eq(OrderStatusHistoryService.BIZ_MEETING_ORDER),
                eq(200L), eq(OrderService.STATUS_PICKUP), eq(OrderService.STATUS_COMPLETED),
                eq(9L), eq(OrderStatusHistoryService.OPERATOR_ADMIN), any());
    }

    @Test
    void adminUpdateStatus_illegal_shouldConflict() {
        when(reservationMapper.selectById(100L)).thenReturn(reservation(100L, 0, 0L, null));

        assertThatThrownBy(() -> service.adminUpdateStatus(100L, 3, 9L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("非法的状态流转");
    }

    @Test
    void reschedule_pending_shouldUpdateOrderAndPayment() {
        MtgReservation r = reservation(100L, 0, 16000L, 200L);
        when(reservationMapper.selectById(100L)).thenReturn(r);
        when(userMapper.selectById(1L)).thenReturn(user(null));
        when(roomMapper.selectOne(any(Wrapper.class))).thenReturn(room()); // 行锁
        when(bookingMapper.selectList(any(Wrapper.class))).thenReturn(List.of()); // 改期无冲突
        when(memberLevelMapper.selectOne(any(Wrapper.class))).thenReturn(basicLevel());
        TrdPayment payment = new TrdPayment();
        payment.setId(900L);
        when(paymentService.getByBiz(PaymentService.BIZ_MEETING, 100L)).thenReturn(payment);

        MeetingRescheduleReq req = new MeetingRescheduleReq();
        req.setReservationDate(LocalDate.now().plusDays(3));
        req.setStartTime(LocalTime.of(9, 0));
        req.setEndTime(LocalTime.of(10, 0)); // 2h → 1h：费用 8000
        MeetingReservationVO vo = service.reschedule(1L, 100L, req);

        assertThat(vo.getFeeAmount()).isEqualTo(8000L);
        assertThat(vo.getReservationDate()).isEqualTo(LocalDate.now().plusDays(3));
        verify(bookingMapper).update(any(), any(Wrapper.class)); // 占用时段更新
        verify(orderMapper).update(any(), any(Wrapper.class));   // 订单金额更新
        verify(paymentService).updateAmount(900L, 8000L);        // 支付单金额更新
    }

    @Test
    void reschedule_paidConfirmed_shouldConflict() {
        MtgReservation r = reservation(100L, 1, 16000L, 200L);
        r.setIsFree(0);
        when(reservationMapper.selectById(100L)).thenReturn(r);

        assertThatThrownBy(() -> service.reschedule(1L, 100L, new MeetingRescheduleReq()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("暂不支持改期");
    }

    @Test
    void reschedule_freeConfirmed_shouldWork() {
        MtgReservation r = reservation(100L, 1, 0L, null);
        r.setIsFree(1);
        when(reservationMapper.selectById(100L)).thenReturn(r);
        when(userMapper.selectById(1L)).thenReturn(user(5L));
        when(enterpriseMapper.selectById(5L)).thenReturn(vipEnterprise());
        when(roomMapper.selectOne(any(Wrapper.class))).thenReturn(room());
        when(bookingMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(memberLevelMapper.selectOne(any(Wrapper.class))).thenReturn(vipLevel());
        when(reservationMapper.selectList(any(Wrapper.class))).thenReturn(List.of()); // 免费额度未用

        MeetingRescheduleReq req = new MeetingRescheduleReq();
        req.setReservationDate(LocalDate.now().plusDays(3));
        req.setStartTime(LocalTime.of(9, 0));
        req.setEndTime(LocalTime.of(10, 0)); // 1h，仍在免费额度内
        MeetingReservationVO vo = service.reschedule(1L, 100L, req);

        assertThat(vo.getFeeAmount()).isZero();
        verify(reservationMapper).updateById(any(MtgReservation.class));
        verify(bookingMapper).update(any(), any(Wrapper.class));
    }

    @Test
    void myReservations_shouldBeReadOnly() {
        when(reservationMapper.selectPage(any(com.baomidou.mybatisplus.extension.plugins.pagination.Page.class),
                any(Wrapper.class))).thenAnswer(inv -> inv.getArgument(0));

        service.myReservations(1L, 1, 10, null);

        // 查询接口必须纯读：原实现会在此惰性置过期、释放占用并取消关联订单
        // （读接口写库 + 无界扫描 + N+1 且无事务，见 docs/缓存与查询效率评估.md §2.0）
        verify(reservationMapper, never()).update(any(), any(Wrapper.class));
        verify(bookingMapper, never()).update(any(), any(Wrapper.class));
        verify(orderMapper, never()).update(any(), any(Wrapper.class));
    }

    @Test
    void expirePastReservationsOnce_shouldExpireAndRelease() {
        MtgReservation past = reservation(100L, 1, 16000L, 200L);
        past.setReservationDate(LocalDate.now().minusDays(1));
        when(reservationMapper.selectList(any(Wrapper.class))).thenReturn(List.of(past));

        int handled = service.expirePastReservationsOnce(MeetingReservationService.EXPIRE_DEFAULT_BATCH_SIZE);

        assertThat(handled).isEqualTo(1);
        verify(reservationMapper).update(any(), any(Wrapper.class)); // 置过期
        verify(bookingMapper).update(any(), any(Wrapper.class));     // 释放占用
        verify(orderMapper).update(any(), any(Wrapper.class));       // 关联订单取消
    }

    @Test
    void expirePastReservationsOnce_shouldReturnZeroWhenNothingToExpire() {
        when(reservationMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        assertThat(service.expirePastReservationsOnce(MeetingReservationService.EXPIRE_DEFAULT_BATCH_SIZE))
                .isZero();
        verify(reservationMapper, never()).update(any(), any(Wrapper.class));
    }

    @Test
    void freeHours_shouldComputeRemaining() {
        when(userMapper.selectById(1L)).thenReturn(user(5L));
        when(enterpriseMapper.selectById(5L)).thenReturn(vipEnterprise());
        when(memberLevelMapper.selectOne(any(Wrapper.class))).thenReturn(vipLevel());
        MtgReservation used = new MtgReservation();
        used.setIsFree(1);
        used.setStatus(1);
        used.setDurationHours(new BigDecimal("2.0"));
        when(reservationMapper.selectList(any(Wrapper.class))).thenReturn(List.of(used));

        var vo = service.freeHours(1L, java.time.YearMonth.now());

        assertThat(vo.getTotalHours()).isEqualByComparingTo("4.0");
        assertThat(vo.getUsedHours()).isEqualByComparingTo("2.0");
        assertThat(vo.getRemainingHours()).isEqualByComparingTo("2.0");
    }
}
