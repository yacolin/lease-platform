package com.example.leaseplatform.mtg.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.mtg.dto.MeetingReservationCreateReq;
import com.example.leaseplatform.mtg.dto.MeetingReservationVO;
import com.example.leaseplatform.mtg.entity.MtgReservation;
import com.example.leaseplatform.mtg.entity.MtgRoom;
import com.example.leaseplatform.mtg.mapper.MtgReservationMapper;
import com.example.leaseplatform.mtg.mapper.MtgRoomMapper;
import com.example.leaseplatform.trd.service.BalanceService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会议室预约服务单元测试：冲突校验/免费时长抵扣/超时计费/支付/取消/完成。
 */
@ExtendWith(MockitoExtension.class)
class MeetingReservationServiceTest {

    @Mock
    private MtgReservationMapper reservationMapper;
    @Mock
    private MtgRoomMapper roomMapper;
    @Mock
    private UsrUserMapper userMapper;
    @Mock
    private UsrEnterpriseMapper enterpriseMapper;
    @Mock
    private UsrMemberLevelMapper memberLevelMapper;
    @Mock
    private BalanceService balanceService;

    private MeetingReservationService service;

    @BeforeAll
    static void initMpEntityCache() {
        initTableInfo(MtgReservation.class);
        initTableInfo(MtgRoom.class);
        initTableInfo(UsrEnterprise.class);
        initTableInfo(UsrMemberLevel.class);
    }

    private static void initTableInfo(Class<?> clazz) {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), clazz);
    }

    @BeforeEach
    void setUp() {
        service = new MeetingReservationService(reservationMapper, roomMapper, userMapper,
                enterpriseMapper, memberLevelMapper, balanceService);
    }

    private MtgRoom room() {
        MtgRoom r = new MtgRoom();
        r.setId(1L);
        r.setRoomName("会议室A");
        r.setHourlyFee(new BigDecimal("80.00"));
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

    private MtgReservation existing(LocalTime start, LocalTime end, int status) {
        MtgReservation r = new MtgReservation();
        r.setRoomId(1L);
        r.setReservationDate(LocalDate.now().plusDays(2));
        r.setStartTime(start);
        r.setEndTime(end);
        r.setStatus(status);
        return r;
    }

    // ==================== 预约 / 冲突 / 计费 ====================

    @Test
    void create_paid_noFreeHours_shouldChargeAndPending() {
        when(userMapper.selectById(1L)).thenReturn(user(null)); // 个人用户无免费时长
        when(roomMapper.selectById(1L)).thenReturn(room());
        when(reservationMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(reservationMapper.insert(any(MtgReservation.class))).thenAnswer(inv -> {
            ((MtgReservation) inv.getArgument(0)).setId(100L);
            return 1;
        });

        MeetingReservationVO vo = service.create(1L, req(LocalTime.of(9, 0), LocalTime.of(11, 0)));

        // 2 小时 × 80 = 160，无免费时长 → 待确认
        assertThat(vo.getFeeAmount()).isEqualByComparingTo("160.00");
        assertThat(vo.getIsFree()).isZero();
        assertThat(vo.getStatus()).isZero();
        verify(balanceService).debit(1L, new BigDecimal("160.00"), null, "会议室预约");
    }

    @Test
    void create_memberFreeHours_shouldBeFree() {
        when(userMapper.selectById(1L)).thenReturn(user(5L));
        when(roomMapper.selectById(1L)).thenReturn(room());
        when(enterpriseMapper.selectById(5L)).thenReturn(vipEnterprise());
        when(memberLevelMapper.selectOne(any(Wrapper.class))).thenReturn(vipLevel()); // 4h/月
        when(reservationMapper.selectList(any(Wrapper.class))).thenReturn(List.of()); // 本月未用
        when(reservationMapper.insert(any(MtgReservation.class))).thenAnswer(inv -> {
            ((MtgReservation) inv.getArgument(0)).setId(100L);
            return 1;
        });

        MeetingReservationVO vo = service.create(1L, req(LocalTime.of(9, 0), LocalTime.of(11, 0)));

        assertThat(vo.getFeeAmount()).isEqualByComparingTo("0.00");
        assertThat(vo.getIsFree()).isEqualTo(1);
        assertThat(vo.getStatus()).isEqualTo(1); // 免费直接已确认
        verify(balanceService, never()).debit(any(), any(), any(), any());
    }

    @Test
    void create_partiallyFree_shouldChargeOvertime() {
        // VIP 4h/月，本月已用 3h（8:00-11:00 免费单）→ 剩余 1h；本次 11:00-13:00（2h）
        // → 免费 1h、超时 1h × 80 = 80（相邻时段无冲突）
        when(userMapper.selectById(1L)).thenReturn(user(5L));
        when(roomMapper.selectById(1L)).thenReturn(room());
        when(enterpriseMapper.selectById(5L)).thenReturn(vipEnterprise());
        when(memberLevelMapper.selectOne(any(Wrapper.class))).thenReturn(vipLevel());
        MtgReservation used = existing(LocalTime.of(8, 0), LocalTime.of(11, 0), 2);
        used.setIsFree(1);
        used.setDurationHours(new BigDecimal("3.0"));
        when(reservationMapper.selectList(any(Wrapper.class))).thenReturn(List.of(used));
        when(reservationMapper.insert(any(MtgReservation.class))).thenAnswer(inv -> {
            ((MtgReservation) inv.getArgument(0)).setId(100L);
            return 1;
        });

        MeetingReservationVO vo = service.create(1L, req(LocalTime.of(11, 0), LocalTime.of(13, 0)));

        assertThat(vo.getFeeAmount()).isEqualByComparingTo("80.00"); // 超时 1h
        assertThat(vo.getIsFree()).isZero();
        assertThat(vo.getStatus()).isZero();
    }

    @Test
    void create_conflict_should409() {
        when(userMapper.selectById(1L)).thenReturn(user(null));
        when(roomMapper.selectById(1L)).thenReturn(room());
        when(reservationMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(existing(LocalTime.of(9, 30), LocalTime.of(10, 30), 1)));

        assertThatThrownBy(() -> service.create(1L, req(LocalTime.of(10, 0), LocalTime.of(11, 0))))
                .isInstanceOf(BizException.class)
                .hasMessage("该时段已被预约");
        verify(reservationMapper, never()).insert(any(MtgReservation.class));
    }

    @Test
    void create_adjacentTime_shouldNotConflict() {
        when(userMapper.selectById(1L)).thenReturn(user(null));
        when(roomMapper.selectById(1L)).thenReturn(room());
        when(reservationMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(existing(LocalTime.of(9, 0), LocalTime.of(10, 0), 1))); // 10:00 结束不重叠
        when(reservationMapper.insert(any(MtgReservation.class))).thenAnswer(inv -> {
            ((MtgReservation) inv.getArgument(0)).setId(100L);
            return 1;
        });

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

    // ==================== 支付 / 取消 / 完成 / 过期 ====================

    @Test
    void pay_shouldConfirm() {
        MtgReservation r = reservation(100L, 0, "160.00");
        when(reservationMapper.selectById(100L)).thenReturn(r);
        when(roomMapper.selectById(1L)).thenReturn(room());

        MeetingReservationVO vo = service.pay(1L, 100L);

        assertThat(vo.getStatus()).isEqualTo(1);
    }

    @Test
    void cancel_paid_shouldRefund() {
        MtgReservation r = reservation(100L, 1, "160.00");
        when(reservationMapper.selectById(100L)).thenReturn(r);
        when(roomMapper.selectById(1L)).thenReturn(room());

        MeetingReservationVO vo = service.cancel(1L, 100L, "改期");

        assertThat(vo.getStatus()).isEqualTo(3);
        assertThat(vo.getCancelReason()).isEqualTo("改期");
        verify(balanceService).credit(1L, new BigDecimal("160.00"), BigDecimal.ZERO,
                BalanceService.TX_REFUND, null, null, "会议室预约取消退款");
    }

    @Test
    void cancel_free_shouldNotRefund() {
        MtgReservation r = reservation(100L, 1, "0.00");
        when(reservationMapper.selectById(100L)).thenReturn(r);
        when(roomMapper.selectById(1L)).thenReturn(room());

        service.cancel(1L, 100L, null);

        verify(balanceService, never()).credit(any(), any(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void adminComplete_shouldComplete() {
        MtgReservation r = reservation(100L, 1, "0.00");
        when(reservationMapper.selectById(100L)).thenReturn(r);
        when(roomMapper.selectById(1L)).thenReturn(room());

        MeetingReservationVO vo = service.adminComplete(100L);

        assertThat(vo.getStatus()).isEqualTo(2);
    }

    @Test
    void myReservations_shouldExpirePast() {
        when(reservationMapper.selectPage(any(com.baomidou.mybatisplus.extension.plugins.pagination.Page.class),
                any(Wrapper.class))).thenAnswer(inv -> inv.getArgument(0));

        service.myReservations(1L, 1, 10, null);

        // 惰性过期批量更新执行
        verify(reservationMapper).update(any(), any(Wrapper.class));
    }

    private MtgReservation reservation(Long id, int status, String fee) {
        MtgReservation r = new MtgReservation();
        r.setId(id);
        r.setRoomId(1L);
        r.setUserId(1L);
        r.setEnterpriseId(0L);
        r.setStatus(status);
        r.setFeeAmount(new BigDecimal(fee));
        r.setDurationHours(new BigDecimal("2.0"));
        return r;
    }
}
