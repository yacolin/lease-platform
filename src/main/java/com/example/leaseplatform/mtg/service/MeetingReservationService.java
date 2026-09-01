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
import com.example.leaseplatform.trd.service.BalanceService;
import com.example.leaseplatform.trd.service.PaymentService;
import com.example.leaseplatform.trd.service.RefundService;
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
 * 会议室预约服务（1.4 会议室资源化）：
 * <pre>
 *   Room（mtg_rooms）→ Booking（mtg_bookings 占用表）→ Reservation（mtg_reservations 业务事实）
 * </pre>
 * - 时间冲突：mtg_rooms 行锁（SELECT ... FOR UPDATE）串行化同会议室并发预约 + mtg_bookings
 *   占用重叠校验（保证 A 10:00-12:00 与 B 11:00-13:00 不能同时成功）；
 * - 预约订单化（1.4.5）：付费预约关联 ord_orders（order_type=4 会议室）+ trd_payments
 *   （biz_type=5 会议室订单），支付/退款统一走 1.2 支付单/退款单，不再自维护一套支付逻辑；
 * - 会员免费时长抵扣 + 超时计费（沿用 1.1 定价模型与价格快照）；
 * - 状态生命周期：0-待确认, 1-已确认, 2-使用中, 3-已完成, 4-已取消, 5-已过期；
 * - 改期（1.4）：待确认（未支付）/ 免费已确认可改，付费已确认需先取消重新预约（差额处理留 2.0）；
 * - 过期惰性处理：查询时把已过预约日且未完成的预约置为已过期并释放占用。
 */
@Service
@RequiredArgsConstructor
public class MeetingReservationService {

    /** 预约状态 */
    public static final int STATUS_PENDING = 0;    // 待确认
    public static final int STATUS_CONFIRMED = 1;  // 已确认
    public static final int STATUS_IN_USE = 2;     // 使用中
    public static final int STATUS_COMPLETED = 3;  // 已完成
    public static final int STATUS_CANCELLED = 4;  // 已取消
    public static final int STATUS_EXPIRED = 5;    // 已过期

    /** 订单类型：会议室（ord_orders.order_type） */
    public static final int ORDER_TYPE_MEETING = 4;

    /** 无企业用户的 enterprise_id 占位 */
    private static final long NO_ENTERPRISE = 0L;

    /** 非会员（个人/无等级）超时单价的兜底等级编码 */
    private static final String BASIC_LEVEL_CODE = "BASIC";

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final MtgReservationMapper reservationMapper;
    private final MtgRoomMapper roomMapper;
    private final MtgRoomLevelPriceMapper roomLevelPriceMapper;
    private final MtgBookingMapper bookingMapper;
    private final UsrUserMapper userMapper;
    private final UsrEnterpriseMapper enterpriseMapper;
    private final UsrMemberLevelMapper memberLevelMapper;
    private final BalanceService balanceService;
    private final PaymentService paymentService;
    private final RefundService refundService;
    private final OrdOrderMapper orderMapper;
    private final OrderStatusHistoryService statusHistoryService;

    // ==================== 预约 ====================

    /** 创建预约：行锁 + 占用冲突校验 + 免费时长抵扣 + 超时计费；付费预约订单化（待支付） */
    @Transactional
    public MeetingReservationVO create(Long userId, MeetingReservationCreateReq req) {
        UsrUser user = requireUser(userId);
        MtgRoom room = requireBookableRoom(req.getRoomId());
        validateTimeWindow(req);
        lockRoom(room.getId());
        checkConflict(room.getId(), req.getReservationDate(), req.getStartTime(), req.getEndTime(), null);

        BigDecimal duration = durationHours(req.getStartTime(), req.getEndTime());
        // 免费时长抵扣 + 超时计费（费用为整数「分」；单价=会议室覆盖价→等级默认价）
        FreeUsage free = calcFreeUsage(user, req.getReservationDate(), duration);
        BigDecimal paidHours = duration.subtract(free.freeHours());
        long unitPrice = resolveOvertimeFee(room.getId(), user);
        long fee = roundCents(paidHours.multiply(BigDecimal.valueOf(unitPrice)));

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
        // 价格快照：下单时单价 + 本次抵扣免费时长（规则可改、快照不变，保证历史单对账）
        reservation.setOvertimeUnitPrice(unitPrice);
        reservation.setFreeHoursDeducted(free.freeHours());
        // 免费预约直接已确认；付费预约待支付（1.4 起不再创建时扣款，支付走 pay()）
        reservation.setStatus(fee == 0 ? STATUS_CONFIRMED : STATUS_PENDING);
        reservationMapper.insert(reservation);

        // 资源占用记录（Booking，占用中）
        insertBooking(reservation);

        // 1.4.5 预约订单化：付费预约关联订单 + 支付单（交易事实），免费预约无交易
        if (fee > 0) {
            linkOrderAndPayment(reservation, user);
        }
        return toVO(reservation, room);
    }

    /** 余额支付付费预约：扣款 → 预约/订单乐观锁定 → 支付单结算 → 已确认 */
    @Transactional
    public MeetingReservationVO pay(Long userId, Long reservationId) {
        MtgReservation reservation = requireOwn(userId, reservationId);
        if (reservation.getStatus() == null || reservation.getStatus() != STATUS_PENDING) {
            throw BizException.conflict("预约已处理");
        }
        if (reservation.getFeeAmount() == null || reservation.getFeeAmount() <= 0) {
            throw BizException.conflict("该预约无需支付");
        }
        // 1. 余额扣款（余额不足时抛异常，此时无任何写操作，预约保持待支付可重试）
        balanceService.debit(userId, reservation.getFeeAmount(), reservation.getOrderId(), "会议室预约");
        // 2. 乐观锁定预约 0→1 与关联订单 0→1（并发重复支付只有一个成功；失败本事务回滚扣款）
        LocalDateTime now = LocalDateTime.now();
        int updatedRes = reservationMapper.update(null, new LambdaUpdateWrapper<MtgReservation>()
                .eq(MtgReservation::getId, reservationId)
                .eq(MtgReservation::getStatus, STATUS_PENDING)
                .set(MtgReservation::getStatus, STATUS_CONFIRMED));
        if (updatedRes == 0) {
            throw BizException.conflict("预约已处理");
        }
        if (reservation.getOrderId() != null) {
            int updatedOrder = orderMapper.update(null, new LambdaUpdateWrapper<OrdOrder>()
                    .eq(OrdOrder::getId, reservation.getOrderId())
                    .eq(OrdOrder::getOrderStatus, OrderService.STATUS_PENDING)
                    .set(OrdOrder::getOrderStatus, OrderService.STATUS_PICKUP)
                    .set(OrdOrder::getPaidAt, now));
            if (updatedOrder == 0) {
                throw BizException.conflict("预约已处理");
            }
            // 3. 支付单结算（幂等，status 0→1）
            TrdPayment payment = paymentService.getByBiz(PaymentService.BIZ_MEETING, reservationId);
            if (payment != null) {
                paymentService.settle(payment.getId(), null);
            }
            // 4. 订单状态历史
            statusHistoryService.record(OrderStatusHistoryService.BIZ_MEETING_ORDER,
                    reservation.getOrderId(), OrderService.STATUS_PENDING, OrderService.STATUS_PICKUP,
                    userId, OrderStatusHistoryService.OPERATOR_USER, "会议室预约支付");
        }
        reservation.setStatus(STATUS_CONFIRMED);
        return toVO(reservation, roomMapper.selectById(reservation.getRoomId()));
    }

    /** 取消：待确认/已确认可取消；已付费原路退款（退款单）+ 释放占用 */
    @Transactional
    public MeetingReservationVO cancel(Long userId, Long reservationId, String reason) {
        MtgReservation reservation = requireOwn(userId, reservationId);
        int status = reservation.getStatus() == null ? STATUS_PENDING : reservation.getStatus();
        if (status == STATUS_IN_USE || status == STATUS_COMPLETED
                || status == STATUS_CANCELLED || status == STATUS_EXPIRED) {
            throw BizException.conflict("预约已结束");
        }
        if (reservation.getFeeAmount() != null && reservation.getFeeAmount() > 0) {
            // 原路退款（1.2 退款单：幂等键防重复退款 + 可退金额校验）
            TrdPayment payment = paymentService.getByBiz(PaymentService.BIZ_MEETING, reservationId);
            if (payment != null) {
                refundService.refundToBalance(userId, payment.getId(), reservation.getFeeAmount(),
                        "会议室预约取消退款", "MEETING_CANCEL_REFUND:" + reservationId,
                        reservation.getOrderId());
            }
            // 关联订单 → 已取消
            if (reservation.getOrderId() != null) {
                orderMapper.update(null, new LambdaUpdateWrapper<OrdOrder>()
                        .eq(OrdOrder::getId, reservation.getOrderId())
                        .set(OrdOrder::getOrderStatus, OrderService.STATUS_CANCELLED)
                        .set(OrdOrder::getCancelledAt, LocalDateTime.now())
                        .set(OrdOrder::getCancelReason, "会议室预约取消"));
                statusHistoryService.record(OrderStatusHistoryService.BIZ_MEETING_ORDER,
                        reservation.getOrderId(), OrderService.STATUS_PICKUP, OrderService.STATUS_CANCELLED,
                        userId, OrderStatusHistoryService.OPERATOR_USER, "会议室预约取消");
            }
        }
        reservation.setStatus(STATUS_CANCELLED);
        reservation.setCancelledAt(LocalDateTime.now());
        reservation.setCancelReason(reason);
        reservationMapper.updateById(reservation);
        releaseBooking(reservationId);
        return toVO(reservation, roomMapper.selectById(reservation.getRoomId()));
    }

    /** 商家状态推进：已确认 → 使用中 → 已完成（完成时完成关联订单 + 释放占用） */
    @Transactional
    public MeetingReservationVO adminUpdateStatus(Long reservationId, Integer target, Long operatorId) {
        MtgReservation reservation = require(reservationId);
        int cur = reservation.getStatus() == null ? STATUS_PENDING : reservation.getStatus();
        boolean valid = switch (target) {
            case STATUS_IN_USE -> cur == STATUS_CONFIRMED;          // 已确认 → 使用中
            case STATUS_COMPLETED -> cur == STATUS_IN_USE;          // 使用中 → 已完成
            default -> false;
        };
        if (!valid) {
            throw BizException.conflict("非法的状态流转：" + cur + " → " + target);
        }
        reservation.setStatus(target);
        reservationMapper.updateById(reservation);
        if (target == STATUS_COMPLETED) {
            // 会议结束：完成关联订单 + 释放占用
            if (reservation.getOrderId() != null) {
                orderMapper.update(null, new LambdaUpdateWrapper<OrdOrder>()
                        .eq(OrdOrder::getId, reservation.getOrderId())
                        .set(OrdOrder::getOrderStatus, OrderService.STATUS_COMPLETED)
                        .set(OrdOrder::getCompletedAt, LocalDateTime.now()));
                statusHistoryService.record(OrderStatusHistoryService.BIZ_MEETING_ORDER,
                        reservation.getOrderId(), OrderService.STATUS_PICKUP, OrderService.STATUS_COMPLETED,
                        operatorId, OrderStatusHistoryService.OPERATOR_ADMIN, "会议完成");
            }
            releaseBooking(reservationId);
        }
        return toVO(reservation, roomMapper.selectById(reservation.getRoomId()));
    }

    /**
     * 改期（1.4）：仅待确认（未支付）或免费已确认可改；付费已确认需先取消重新预约
     * （差额处理留 2.0 Payment Center）。改期重算费用并同步订单/支付单金额。
     */
    @Transactional
    public MeetingReservationVO reschedule(Long userId, Long reservationId, MeetingRescheduleReq req) {
        MtgReservation reservation = requireOwn(userId, reservationId);
        int status = reservation.getStatus() == null ? STATUS_PENDING : reservation.getStatus();
        if (status == STATUS_IN_USE || status == STATUS_COMPLETED
                || status == STATUS_CANCELLED || status == STATUS_EXPIRED) {
            throw BizException.conflict("预约已结束，不可改期");
        }
        if (status == STATUS_CONFIRMED && (reservation.getIsFree() == null || reservation.getIsFree() != 1)) {
            throw BizException.conflict("已付费预约暂不支持改期，请先取消后重新预约");
        }
        validateTimeWindow(req);
        lockRoom(reservation.getRoomId());
        checkConflict(reservation.getRoomId(), req.getReservationDate(),
                req.getStartTime(), req.getEndTime(), reservation.getId());

        BigDecimal duration = durationHours(req.getStartTime(), req.getEndTime());
        UsrUser user = requireUser(userId);
        FreeUsage free = calcFreeUsage(user, req.getReservationDate(), duration);
        BigDecimal paidHours = duration.subtract(free.freeHours());
        long unitPrice = resolveOvertimeFee(reservation.getRoomId(), user);
        long fee = roundCents(paidHours.multiply(BigDecimal.valueOf(unitPrice)));
        if (status == STATUS_CONFIRMED && fee > 0) {
            throw BizException.conflict("改期后超出免费时长需付费，请取消后重新预约");
        }

        // 更新预约（时段/时长/费用/价格快照）
        reservation.setReservationDate(req.getReservationDate());
        reservation.setStartTime(req.getStartTime());
        reservation.setEndTime(req.getEndTime());
        reservation.setDurationHours(duration);
        reservation.setIsFree(paidHours.compareTo(ZERO) == 0 ? 1 : 0);
        reservation.setFeeAmount(fee);
        reservation.setOvertimeUnitPrice(unitPrice);
        reservation.setFreeHoursDeducted(free.freeHours());
        reservationMapper.updateById(reservation);
        // 更新占用记录时间
        bookingMapper.update(null, new LambdaUpdateWrapper<MtgBooking>()
                .eq(MtgBooking::getReservationId, reservationId)
                .set(MtgBooking::getStartAt, LocalDateTime.of(req.getReservationDate(), req.getStartTime()))
                .set(MtgBooking::getEndAt, LocalDateTime.of(req.getReservationDate(), req.getEndTime())));
        // 待确认（未支付）→ 同步订单应付与支付单金额
        if (reservation.getOrderId() != null) {
            orderMapper.update(null, new LambdaUpdateWrapper<OrdOrder>()
                    .eq(OrdOrder::getId, reservation.getOrderId())
                    .set(OrdOrder::getPayableAmount, fee)
                    .set(OrdOrder::getTotalAmount, fee));
            TrdPayment payment = paymentService.getByBiz(PaymentService.BIZ_MEETING, reservationId);
            if (payment != null) {
                paymentService.updateAmount(payment.getId(), fee);
            }
        }
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
                .in(MtgReservation::getStatus, List.of(STATUS_CONFIRMED, STATUS_IN_USE, STATUS_COMPLETED)));
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
        validateTimeWindow(req.getReservationDate(), req.getStartTime(), req.getEndTime());
    }

    private void validateTimeWindow(MeetingRescheduleReq req) {
        validateTimeWindow(req.getReservationDate(), req.getStartTime(), req.getEndTime());
    }

    private void validateTimeWindow(LocalDate date, LocalTime start, LocalTime end) {
        LocalDate today = LocalDate.now();
        if (date.isBefore(today.plusDays(1)) || date.isAfter(today.plusDays(7))) {
            throw BizException.badRequest("可预约日期为 " + today.plusDays(1) + " ~ " + today.plusDays(7));
        }
        if (!start.isBefore(end)) {
            throw BizException.badRequest("开始时间必须早于结束时间");
        }
        if (start.isBefore(LocalTime.of(8, 0)) || end.isAfter(LocalTime.of(22, 0))) {
            throw BizException.badRequest("预约时段须在 08:00 ~ 22:00 内");
        }
    }

    /** 会议室行锁：SELECT ... FOR UPDATE 串行化同会议室并发预约 */
    private void lockRoom(Long roomId) {
        roomMapper.selectOne(new LambdaQueryWrapper<MtgRoom>()
                .eq(MtgRoom::getId, roomId)
                .last("FOR UPDATE"));
    }

    /** 占用冲突校验：查 mtg_bookings（占用中）同会议室时间重叠；excludeReservationId 为改期排除自身 */
    private void checkConflict(Long roomId, LocalDate date, LocalTime start, LocalTime end,
                               Long excludeReservationId) {
        LocalDateTime startAt = LocalDateTime.of(date, start);
        LocalDateTime endAt = LocalDateTime.of(date, end);
        List<MtgBooking> bookings = bookingMapper.selectList(new LambdaQueryWrapper<MtgBooking>()
                .eq(MtgBooking::getRoomId, roomId)
                .eq(MtgBooking::getStatus, MtgBooking.STATUS_OCCUPIED)
                .lt(MtgBooking::getStartAt, endAt)
                .gt(MtgBooking::getEndAt, startAt)
                .ne(excludeReservationId != null, MtgBooking::getReservationId, excludeReservationId));
        if (!bookings.isEmpty()) {
            throw BizException.conflict("该时段已被预约");
        }
    }

    /** 创建占用记录（Booking，占用中） */
    private void insertBooking(MtgReservation reservation) {
        MtgBooking booking = new MtgBooking();
        booking.setRoomId(reservation.getRoomId());
        booking.setReservationId(reservation.getId());
        booking.setStartAt(LocalDateTime.of(reservation.getReservationDate(), reservation.getStartTime()));
        booking.setEndAt(LocalDateTime.of(reservation.getReservationDate(), reservation.getEndTime()));
        booking.setStatus(MtgBooking.STATUS_OCCUPIED);
        bookingMapper.insert(booking);
    }

    /** 释放占用（取消/完成/过期） */
    private void releaseBooking(Long reservationId) {
        bookingMapper.update(null, new LambdaUpdateWrapper<MtgBooking>()
                .eq(MtgBooking::getReservationId, reservationId)
                .eq(MtgBooking::getStatus, MtgBooking.STATUS_OCCUPIED)
                .set(MtgBooking::getStatus, MtgBooking.STATUS_RELEASED));
    }

    /** 1.4.5 预约订单化：创建关联订单（order_type=4 会议室）+ 支付单（biz_type=5 会议室订单，待支付） */
    private void linkOrderAndPayment(MtgReservation reservation, UsrUser user) {
        OrdOrder order = new OrdOrder();
        order.setOrderNo(generateNo("RO"));
        order.setUserId(reservation.getUserId());
        order.setEnterpriseId(reservation.getEnterpriseId() == NO_ENTERPRISE ? null : reservation.getEnterpriseId());
        order.setOrderType(ORDER_TYPE_MEETING);
        order.setOrderStatus(OrderService.STATUS_PENDING);
        order.setTotalAmount(reservation.getFeeAmount());
        order.setPayableAmount(reservation.getFeeAmount());
        order.setPaymentMethod(1); // 余额支付
        order.setReservationDate(reservation.getReservationDate());
        order.setReservationTime(reservation.getStartTime());
        order.setRemark(reservation.getMeetingTopic());
        orderMapper.insert(order);
        reservation.setOrderId(order.getId());
        reservationMapper.updateById(reservation);
        paymentService.create(reservation.getUserId(), PaymentService.BIZ_MEETING, reservation.getId(),
                reservation.getFeeAmount(), PaymentService.METHOD_BALANCE, PaymentService.CHANNEL_BALANCE,
                order.getOrderNo());
        statusHistoryService.record(OrderStatusHistoryService.BIZ_MEETING_ORDER, order.getId(),
                null, OrderService.STATUS_PENDING, reservation.getUserId(),
                OrderStatusHistoryService.OPERATOR_USER, "会议室预约下单");
    }

    private BigDecimal durationHours(LocalTime start, LocalTime end) {
        long minutes = java.time.Duration.between(start, end).toMinutes();
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 1, RoundingMode.HALF_UP);
    }

    /** 金额（分）乘时长后舍入到整数分（HALF_UP） */
    private static long roundCents(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /** 免费时长使用计算：免费部分 + 付费部分 */
    private FreeUsage calcFreeUsage(UsrUser user, LocalDate date, BigDecimal duration) {
        BigDecimal monthlyFree = monthlyFreeHours(user);
        if (monthlyFree.compareTo(ZERO) <= 0) {
            return new FreeUsage(ZERO, duration);
        }
        // 本月已用免费时长（同企业，is_free=1，已确认/使用中/已完成）
        LocalDate monthStart = date.withDayOfMonth(1);
        LocalDate monthEnd = monthStart.plusMonths(1);
        BigDecimal used = ZERO;
        List<MtgReservation> freeRes = reservationMapper.selectList(new LambdaQueryWrapper<MtgReservation>()
                .eq(MtgReservation::getEnterpriseId, enterpriseKey(user))
                .ge(MtgReservation::getReservationDate, monthStart)
                .lt(MtgReservation::getReservationDate, monthEnd)
                .eq(MtgReservation::getIsFree, 1)
                .in(MtgReservation::getStatus, List.of(STATUS_CONFIRMED, STATUS_IN_USE, STATUS_COMPLETED)));
        for (MtgReservation r : freeRes) {
            used = used.add(r.getDurationHours());
        }
        BigDecimal remaining = monthlyFree.subtract(used).max(ZERO);
        BigDecimal freeHours = duration.min(remaining);
        return new FreeUsage(freeHours, duration.subtract(freeHours));
    }

    /** 企业会员每月免费时长（小时）：企业有效会员等级的 monthly_meeting_hours；个人/无会员 0 */
    private BigDecimal monthlyFreeHours(UsrUser user) {
        UsrMemberLevel level = levelByCode(effectiveLevelCode(user));
        return level == null || level.getMonthlyMeetingHours() == null
                ? ZERO : BigDecimal.valueOf(level.getMonthlyMeetingHours());
    }

    /** 用户当前有效会员等级编码；个人/无企业/等级已过期 → null */
    private String effectiveLevelCode(UsrUser user) {
        Long enterpriseId = user.getEnterpriseId();
        if (enterpriseId == null) {
            return null;
        }
        UsrEnterprise enterprise = enterpriseMapper.selectById(enterpriseId);
        if (enterprise == null || EnterpriseService.effectiveMemberLevel(enterprise) <= 0) {
            return null;
        }
        return switch (enterprise.getMemberLevel()) {
            case 1 -> "BASIC";
            case 2 -> "VIP";
            case 3 -> "SVIP";
            default -> null;
        };
    }

    /** 按编码查启用中的会员等级配置 */
    private UsrMemberLevel levelByCode(String levelCode) {
        if (levelCode == null) {
            return null;
        }
        return memberLevelMapper.selectOne(new LambdaQueryWrapper<UsrMemberLevel>()
                .eq(UsrMemberLevel::getLevelCode, levelCode)
                .eq(UsrMemberLevel::getStatus, 1));
    }

    /** 超时单价（分/小时）：会议室等级定价覆盖价 → 等级默认价（非会员按 BASIC 兜底）→ 0 */
    private long resolveOvertimeFee(Long roomId, UsrUser user) {
        String levelCode = effectiveLevelCode(user);
        if (levelCode != null) {
            MtgRoomLevelPrice override = roomLevelPriceMapper.selectOne(new LambdaQueryWrapper<MtgRoomLevelPrice>()
                    .eq(MtgRoomLevelPrice::getRoomId, roomId)
                    .eq(MtgRoomLevelPrice::getLevelCode, levelCode)
                    .eq(MtgRoomLevelPrice::getIsActive, 1));
            if (override != null && override.getOvertimeFee() != null) {
                return override.getOvertimeFee();
            }
        }
        UsrMemberLevel level = levelByCode(levelCode == null ? BASIC_LEVEL_CODE : levelCode);
        return level == null || level.getMeetingOvertimeFee() == null
                ? 0L : level.getMeetingOvertimeFee();
    }

    private long enterpriseKey(UsrUser user) {
        return user.getEnterpriseId() == null ? NO_ENTERPRISE : user.getEnterpriseId();
    }

    /** 惰性过期：已过预约日且仍待确认/已确认 → 已过期（过期不退款），释放占用并取消关联订单 */
    private void expirePastReservations() {
        List<MtgReservation> expired = reservationMapper.selectList(new LambdaQueryWrapper<MtgReservation>()
                .lt(MtgReservation::getReservationDate, LocalDate.now())
                .in(MtgReservation::getStatus, List.of(STATUS_PENDING, STATUS_CONFIRMED)));
        if (expired.isEmpty()) {
            return;
        }
        List<Long> ids = expired.stream().map(MtgReservation::getId).toList();
        reservationMapper.update(null, new LambdaUpdateWrapper<MtgReservation>()
                .in(MtgReservation::getId, ids)
                .set(MtgReservation::getStatus, STATUS_EXPIRED));
        for (Long id : ids) {
            releaseBooking(id);
        }
        for (MtgReservation r : expired) {
            if (r.getOrderId() != null) {
                orderMapper.update(null, new LambdaUpdateWrapper<OrdOrder>()
                        .eq(OrdOrder::getId, r.getOrderId())
                        .set(OrdOrder::getOrderStatus, OrderService.STATUS_CANCELLED)
                        .set(OrdOrder::getCancelledAt, LocalDateTime.now())
                        .set(OrdOrder::getCancelReason, "会议室预约过期"));
                statusHistoryService.record(OrderStatusHistoryService.BIZ_MEETING_ORDER,
                        r.getOrderId(), OrderService.STATUS_PICKUP, OrderService.STATUS_CANCELLED,
                        null, OrderStatusHistoryService.OPERATOR_ADMIN, "会议室预约过期");
            }
        }
    }

    private MtgRoom requireBookableRoom(Long roomId) {
        MtgRoom room = roomMapper.selectById(roomId);
        if (room == null || room.getStatus() == null || room.getStatus() != 1) {
            throw BizException.badRequest("会议室不存在或不可预约");
        }
        return room;
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
        vo.setOrderId(r.getOrderId());
        vo.setReservationDate(r.getReservationDate());
        vo.setStartTime(r.getStartTime());
        vo.setEndTime(r.getEndTime());
        vo.setDurationHours(r.getDurationHours());
        vo.setMeetingTopic(r.getMeetingTopic());
        vo.setStatus(r.getStatus());
        vo.setIsFree(r.getIsFree());
        vo.setFeeAmount(r.getFeeAmount());
        vo.setOvertimeUnitPrice(r.getOvertimeUnitPrice());
        vo.setFreeHoursDeducted(r.getFreeHoursDeducted());
        vo.setCancelledAt(TimeUtil.toEpochMillis(r.getCancelledAt()));
        vo.setCancelReason(r.getCancelReason());
        vo.setCreatedAt(TimeUtil.toEpochMillis(r.getCreatedAt()));
        return vo;
    }

    /** 免费/付费时长拆分结果 */
    private record FreeUsage(BigDecimal freeHours, BigDecimal paidHours) {
    }
}
