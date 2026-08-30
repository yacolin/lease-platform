package com.example.leaseplatform.mtg.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.common.TimeUtil;
import com.example.leaseplatform.mtg.dto.MeetingFreeHoursVO;
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
import com.example.leaseplatform.usr.service.EnterpriseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 会议室预约服务：
 * - 时段冲突校验：同会议室/同日期下状态为待确认/已确认且时间重叠 → 409；
 * - 会员免费时长抵扣：企业会员等级（usr_member_levels.monthly_meeting_hours）按自然月
 *   统计已用免费时长（status=1/2 且 is_free=1），本次预约优先抵扣剩余免费时长；
 * - 超时计费：超出免费时长的部分 × 会议室 hourly_fee（元/小时）；
 * - 状态流转：待确认（需付费）→ 余额支付 → 已确认 → 已完成/已取消（退款）/惰性过期；
 * - 过期惰性处理：查询时把已过预约日且未完成的预约置为已过期（4）。
 */
@Service
@RequiredArgsConstructor
public class MeetingReservationService {

    /** 预约状态 */
    public static final int STATUS_PENDING = 0;   // 待确认
    public static final int STATUS_CONFIRMED = 1; // 已确认
    public static final int STATUS_COMPLETED = 2; // 已完成
    public static final int STATUS_CANCELLED = 3; // 已取消
    public static final int STATUS_EXPIRED = 4;   // 已过期

    /** 无企业用户的 enterprise_id 占位 */
    private static final long NO_ENTERPRISE = 0L;

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final MtgReservationMapper reservationMapper;
    private final MtgRoomMapper roomMapper;
    private final UsrUserMapper userMapper;
    private final UsrEnterpriseMapper enterpriseMapper;
    private final UsrMemberLevelMapper memberLevelMapper;
    private final BalanceService balanceService;

    // ==================== 预约 ====================

    /** 创建预约：冲突校验 + 免费时长抵扣 + 超时计费；fee=0 直接已确认，否则待确认待支付 */
    @Transactional
    public MeetingReservationVO create(Long userId, MeetingReservationCreateReq req) {
        UsrUser user = requireUser(userId);
        MtgRoom room = roomMapper.selectById(req.getRoomId());
        if (room == null || room.getStatus() == null || room.getStatus() != 1) {
            throw BizException.badRequest("会议室不存在或不可预约");
        }
        validateTimeWindow(req);
        checkConflict(room.getId(), req.getReservationDate(), req.getStartTime(), req.getEndTime());

        BigDecimal duration = durationHours(req.getStartTime(), req.getEndTime());
        // 免费时长抵扣 + 超时计费
        FreeUsage free = calcFreeUsage(user, req.getReservationDate(), duration);
        BigDecimal paidHours = duration.subtract(free.freeHours());
        BigDecimal fee = paidHours.multiply(room.getHourlyFee()).setScale(2, RoundingMode.HALF_UP);

        MtgReservation reservation = new MtgReservation();
        reservation.setReservationNo(generateNo("MR"));
        reservation.setRoomId(room.getId());
        reservation.setUserId(userId);
        reservation.setEnterpriseId(user.getEnterpriseId() == null ? NO_ENTERPRISE : user.getEnterpriseId());
        reservation.setReservationDate(req.getReservationDate());
        reservation.setStartTime(req.getStartTime());
        reservation.setEndTime(req.getEndTime());
        reservation.setDurationHours(duration);
        reservation.setMeetingTopic(req.getMeetingTopic());
        reservation.setIsFree(paidHours.compareTo(ZERO) == 0 ? 1 : 0);
        reservation.setFeeAmount(fee);
        // 免费预约直接已确认；付费预约待支付（余额不足则创建失败）
        if (fee.compareTo(ZERO) == 0) {
            reservation.setStatus(STATUS_CONFIRMED);
        } else {
            reservation.setStatus(STATUS_PENDING);
            // 付费预约：余额立即扣减（余额不足 → 409，预约不创建）
            balanceService.debit(userId, fee, null, "会议室预约");
        }
        reservationMapper.insert(reservation);
        return toVO(reservation, room);
    }

    /** 余额支付付费预约：待确认 → 已确认 */
    @Transactional
    public MeetingReservationVO pay(Long userId, Long reservationId) {
        MtgReservation reservation = requireOwn(userId, reservationId);
        if (reservation.getStatus() == null || reservation.getStatus() != STATUS_PENDING) {
            throw BizException.conflict("预约已处理");
        }
        reservation.setStatus(STATUS_CONFIRMED);
        reservationMapper.updateById(reservation);
        return toVO(reservation, roomMapper.selectById(reservation.getRoomId()));
    }

    /** 取消：待确认/已确认可取消；已付费原路退款 */
    @Transactional
    public MeetingReservationVO cancel(Long userId, Long reservationId, String reason) {
        MtgReservation reservation = requireOwn(userId, reservationId);
        int status = reservation.getStatus() == null ? STATUS_PENDING : reservation.getStatus();
        if (status == STATUS_COMPLETED || status == STATUS_CANCELLED || status == STATUS_EXPIRED) {
            throw BizException.conflict("预约已结束");
        }
        if (reservation.getFeeAmount() != null && reservation.getFeeAmount().compareTo(ZERO) > 0) {
            balanceService.credit(userId, reservation.getFeeAmount(), ZERO,
                    BalanceService.TX_REFUND, null, null, "会议室预约取消退款");
        }
        reservation.setStatus(STATUS_CANCELLED);
        reservation.setCancelledAt(LocalDateTime.now());
        reservation.setCancelReason(reason);
        reservationMapper.updateById(reservation);
        return toVO(reservation, roomMapper.selectById(reservation.getRoomId()));
    }

    /** 商家确认完成：已确认 → 已完成 */
    @Transactional
    public MeetingReservationVO adminComplete(Long reservationId) {
        MtgReservation reservation = require(reservationId);
        if (reservation.getStatus() == null || reservation.getStatus() != STATUS_CONFIRMED) {
            throw BizException.conflict("仅已确认的预约可完成");
        }
        reservation.setStatus(STATUS_COMPLETED);
        reservationMapper.updateById(reservation);
        return toVO(reservation, roomMapper.selectById(reservation.getRoomId()));
    }

    // ==================== 查询 ====================

    public PageResult<MeetingReservationVO> myReservations(Long userId, int page, int size, Integer status) {
        expirePastReservations();
        Page<MtgReservation> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 1000));
        reservationMapper.selectPage(p, new LambdaQueryWrapper<MtgReservation>()
                .eq(MtgReservation::getUserId, userId)
                .eq(status != null, MtgReservation::getStatus, status)
                .orderByDesc(MtgReservation::getId));
        return PageResult.of(p.getTotal(), withRooms(p.getRecords()));
    }

    public MeetingReservationVO getMine(Long userId, Long reservationId) {
        MtgReservation reservation = requireOwn(userId, reservationId);
        return toVO(reservation, roomMapper.selectById(reservation.getRoomId()));
    }

    public PageResult<MeetingReservationVO> adminPage(int page, int size, LocalDate date, Long roomId, Integer status) {
        expirePastReservations();
        Page<MtgReservation> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 1000));
        reservationMapper.selectPage(p, new LambdaQueryWrapper<MtgReservation>()
                .eq(date != null, MtgReservation::getReservationDate, date)
                .eq(roomId != null, MtgReservation::getRoomId, roomId)
                .eq(status != null, MtgReservation::getStatus, status)
                .orderByDesc(MtgReservation::getId));
        return PageResult.of(p.getTotal(), withRooms(p.getRecords()));
    }

    public MeetingReservationVO adminGet(Long reservationId) {
        MtgReservation reservation = require(reservationId);
        return toVO(reservation, roomMapper.selectById(reservation.getRoomId()));
    }

    /** 指定月份剩余免费时长（缺省当月） */
    public MeetingFreeHoursVO freeHours(Long userId, java.time.YearMonth month) {
        UsrUser user = requireUser(userId);
        java.time.YearMonth target = month == null ? java.time.YearMonth.now() : month;
        BigDecimal total = monthlyFreeHours(user);
        LocalDate monthStart = target.atDay(1);
        LocalDate monthEnd = target.plusMonths(1).atDay(1);
        BigDecimal used = ZERO;
        List<MtgReservation> freeRes = reservationMapper.selectList(new LambdaQueryWrapper<MtgReservation>()
                .eq(MtgReservation::getEnterpriseId, enterpriseKey(user))
                .ge(MtgReservation::getReservationDate, monthStart)
                .lt(MtgReservation::getReservationDate, monthEnd)
                .eq(MtgReservation::getIsFree, 1)
                .in(MtgReservation::getStatus, List.of(STATUS_CONFIRMED, STATUS_COMPLETED)));
        for (MtgReservation r : freeRes) {
            used = used.add(r.getDurationHours());
        }
        MeetingFreeHoursVO vo = new MeetingFreeHoursVO();
        vo.setTotalHours(total);
        vo.setUsedHours(used);
        vo.setRemainingHours(total.subtract(used).max(ZERO));
        return vo;
    }

    // ==================== 规则 / 内部 ====================

    /** 预约窗口与时段合法性：最早明天、最远 7 天；8:00-22:00；start < end */
    private void validateTimeWindow(MeetingReservationCreateReq req) {
        LocalDate today = LocalDate.now();
        if (req.getReservationDate().isBefore(today.plusDays(1))
                || req.getReservationDate().isAfter(today.plusDays(7))) {
            throw BizException.badRequest("可预约日期为 " + today.plusDays(1) + " ~ " + today.plusDays(7));
        }
        if (!req.getStartTime().isBefore(req.getEndTime())) {
            throw BizException.badRequest("开始时间必须早于结束时间");
        }
        if (req.getStartTime().isBefore(LocalTime.of(8, 0))
                || req.getEndTime().isAfter(LocalTime.of(22, 0))) {
            throw BizException.badRequest("预约时段须在 08:00 ~ 22:00 内");
        }
    }

    /** 时段冲突校验：同会议室/同日期，待确认或已确认且时间重叠 */
    private void checkConflict(Long roomId, LocalDate date, LocalTime start, LocalTime end) {
        List<MtgReservation> existing = reservationMapper.selectList(new LambdaQueryWrapper<MtgReservation>()
                .eq(MtgReservation::getRoomId, roomId)
                .eq(MtgReservation::getReservationDate, date)
                .in(MtgReservation::getStatus, List.of(STATUS_PENDING, STATUS_CONFIRMED)));
        for (MtgReservation e : existing) {
            if (start.isBefore(e.getEndTime()) && end.isAfter(e.getStartTime())) {
                throw BizException.conflict("该时段已被预约");
            }
        }
    }

    private BigDecimal durationHours(LocalTime start, LocalTime end) {
        long minutes = java.time.Duration.between(start, end).toMinutes();
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 1, RoundingMode.HALF_UP);
    }

    /** 免费时长使用计算：免费部分 + 付费部分 */
    private FreeUsage calcFreeUsage(UsrUser user, LocalDate date, BigDecimal duration) {
        BigDecimal monthlyFree = monthlyFreeHours(user);
        if (monthlyFree.compareTo(ZERO) <= 0) {
            return new FreeUsage(ZERO, duration);
        }
        // 本月已用免费时长（同企业，is_free=1，已确认/已完成）
        LocalDate monthStart = date.withDayOfMonth(1);
        LocalDate monthEnd = monthStart.plusMonths(1);
        BigDecimal used = ZERO;
        List<MtgReservation> freeRes = reservationMapper.selectList(new LambdaQueryWrapper<MtgReservation>()
                .eq(MtgReservation::getEnterpriseId, enterpriseKey(user))
                .ge(MtgReservation::getReservationDate, monthStart)
                .lt(MtgReservation::getReservationDate, monthEnd)
                .eq(MtgReservation::getIsFree, 1)
                .in(MtgReservation::getStatus, List.of(STATUS_CONFIRMED, STATUS_COMPLETED)));
        for (MtgReservation r : freeRes) {
            used = used.add(r.getDurationHours());
        }
        BigDecimal remaining = monthlyFree.subtract(used).max(ZERO);
        BigDecimal freeHours = duration.min(remaining);
        return new FreeUsage(freeHours, duration.subtract(freeHours));
    }

    /** 企业会员每月免费时长（小时）：企业有效会员等级的 monthly_meeting_hours；个人/无会员 0 */
    private BigDecimal monthlyFreeHours(UsrUser user) {
        Long enterpriseId = user.getEnterpriseId();
        if (enterpriseId == null) {
            return ZERO;
        }
        UsrEnterprise enterprise = enterpriseMapper.selectById(enterpriseId);
        if (enterprise == null || EnterpriseService.effectiveMemberLevel(enterprise) <= 0) {
            return ZERO;
        }
        String levelCode = switch (enterprise.getMemberLevel()) {
            case 1 -> "BASIC";
            case 2 -> "VIP";
            case 3 -> "SVIP";
            default -> null;
        };
        if (levelCode == null) {
            return ZERO;
        }
        UsrMemberLevel level = memberLevelMapper.selectOne(new LambdaQueryWrapper<UsrMemberLevel>()
                .eq(UsrMemberLevel::getLevelCode, levelCode)
                .eq(UsrMemberLevel::getStatus, 1));
        return level == null || level.getMonthlyMeetingHours() == null
                ? ZERO : BigDecimal.valueOf(level.getMonthlyMeetingHours());
    }

    private long enterpriseKey(UsrUser user) {
        return user.getEnterpriseId() == null ? NO_ENTERPRISE : user.getEnterpriseId();
    }

    /** 惰性过期：已过预约日且仍待确认/已确认 → 已过期（过期不退款） */
    private void expirePastReservations() {
        reservationMapper.update(null, new LambdaUpdateWrapper<MtgReservation>()
                .lt(MtgReservation::getReservationDate, LocalDate.now())
                .in(MtgReservation::getStatus, List.of(STATUS_PENDING, STATUS_CONFIRMED))
                .set(MtgReservation::getStatus, STATUS_EXPIRED));
    }

    private String generateNo(String prefix) {
        return prefix + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + new java.security.SecureRandom().nextInt(1000, 10000);
    }

    private MtgReservation requireOwn(Long userId, Long reservationId) {
        MtgReservation reservation = reservationMapper.selectById(reservationId);
        if (reservation == null || !reservation.getUserId().equals(userId)) {
            throw BizException.notFound("预约不存在");
        }
        return reservation;
    }

    private MtgReservation require(Long reservationId) {
        MtgReservation reservation = reservationMapper.selectById(reservationId);
        if (reservation == null) {
            throw BizException.notFound("预约不存在");
        }
        return reservation;
    }

    private UsrUser requireUser(Long id) {
        UsrUser user = userMapper.selectById(id);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        return user;
    }

    private List<MeetingReservationVO> withRooms(List<MtgReservation> reservations) {
        if (reservations.isEmpty()) {
            return List.of();
        }
        List<Long> roomIds = reservations.stream().map(MtgReservation::getRoomId).distinct().toList();
        Map<Long, MtgRoom> rooms = roomMapper.selectBatchIds(roomIds).stream()
                .collect(Collectors.toMap(MtgRoom::getId, Function.identity()));
        return reservations.stream()
                .map(r -> toVO(r, rooms.get(r.getRoomId())))
                .toList();
    }

    private MeetingReservationVO toVO(MtgReservation r, MtgRoom room) {
        MeetingReservationVO vo = new MeetingReservationVO();
        vo.setId(r.getId());
        vo.setReservationNo(r.getReservationNo());
        vo.setRoomId(r.getRoomId());
        vo.setRoomName(room == null ? null : room.getRoomName());
        vo.setEnterpriseId(r.getEnterpriseId());
        vo.setReservationDate(r.getReservationDate());
        vo.setStartTime(r.getStartTime());
        vo.setEndTime(r.getEndTime());
        vo.setDurationHours(r.getDurationHours());
        vo.setMeetingTopic(r.getMeetingTopic());
        vo.setStatus(r.getStatus());
        vo.setIsFree(r.getIsFree());
        vo.setFeeAmount(r.getFeeAmount());
        vo.setCancelledAt(TimeUtil.toEpochMillis(r.getCancelledAt()));
        vo.setCancelReason(r.getCancelReason());
        vo.setCreatedAt(TimeUtil.toEpochMillis(r.getCreatedAt()));
        return vo;
    }

    /** 免费/付费时长拆分结果 */
    private record FreeUsage(BigDecimal freeHours, BigDecimal paidHours) {
    }
}
