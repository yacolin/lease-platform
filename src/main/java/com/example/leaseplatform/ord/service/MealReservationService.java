package com.example.leaseplatform.ord.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.common.TimeUtil;
import com.example.leaseplatform.ord.dto.MealReservationCreateReq;
import com.example.leaseplatform.ord.dto.MealReservationItemVO;
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
import com.example.leaseplatform.trd.service.RefundService;
import com.example.leaseplatform.usr.entity.UsrUser;
import com.example.leaseplatform.usr.mapper.UsrUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

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
 * 正餐预订服务：
 * - 规则校验：提前 1 天（晚 8 点后最早后天）、可订未来 3 天、时段仅午餐/晚餐、该日期有菜单；
 * - 创建：套餐快照 + 当天菜单菜品明细快照（dish_details）→ ord_meal_reservations + items，
 *   并关联创建 ord_orders（order_type=2，统一支付/状态体系）；
 * - 折扣复用 {@link DiscountCalculator}（会员×充值叠加）；配送费：周边配送 5 元，其余 0；
 * - 支付（余额 debit）/ 取消（退款）/ 商家备餐状态流转（1→2→3；1/2→5 退款），同步关联订单。
 */
@Service
@RequiredArgsConstructor
public class MealReservationService {

    /** 预订状态（与 ord_orders.order_status 对齐） */
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_READY = 1;    // 已支付/待备餐
    public static final int STATUS_MAKING = 2;   // 备餐中
    public static final int STATUS_COMPLETED = 3;
    public static final int STATUS_CANCELLED = 4;
    public static final int STATUS_REFUNDED = 5;

    /** 配送费：周边配送 5 元（500 分），自取/楼内 0 */
    private static final long SURROUNDING_DELIVERY_FEE = 500L;
    private static final int DELIVERY_SURROUNDING = 3;

    private static final long DELIVERY_ZERO = 0L;

    private final OrdMealReservationMapper reservationMapper;
    private final OrdMealReservationItemMapper itemMapper;
    private final OrdOrderMapper orderMapper;
    private final PrdProductMapper productMapper;
    private final PrdDailyMenuMapper menuMapper;
    private final UsrUserMapper userMapper;
    private final DiscountCalculator discountCalculator;
    private final BalanceService balanceService;
    private final PaymentService paymentService;
    private final RefundService refundService;
    private final OrderStatusHistoryService statusHistoryService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 预订 ====================

    /** 正餐预订：规则校验 + 快照 + 关联订单（待支付） */
    @Transactional
    public MealReservationVO create(Long userId, MealReservationCreateReq req) {
        validateBookingWindow(req.getMenuDate());
        UsrUser user = requireUser(userId);
        PrdProduct product = productMapper.selectById(req.getProductId());
        if (product == null || product.getIsAvailable() == null || product.getIsAvailable() != 1) {
            throw BizException.badRequest("套餐商品已下架或不存在");
        }
        // 该日期有该套餐菜单（供应中）
        Long menuCount = menuMapper.selectCount(new LambdaQueryWrapper<PrdDailyMenu>()
                .eq(PrdDailyMenu::getMenuDate, req.getMenuDate())
                .eq(PrdDailyMenu::getProductId, req.getProductId())
                .eq(PrdDailyMenu::getIsAvailable, 1));
        if (menuCount == null || menuCount == 0) {
            throw BizException.badRequest("该日期暂无此套餐菜单");
        }
        if (req.getDeliveryType() == DELIVERY_SURROUNDING
                && (req.getDeliveryAddress() == null || req.getDeliveryAddress().isBlank())) {
            throw BizException.badRequest("周边配送请填写配送地址");
        }

        // 折扣（会员 × 充值叠加；金额整数「分」，仅折扣率用 BigDecimal 乘法后舍入到分）
        BigDecimal memberRate = discountCalculator.memberDiscountRate(user);
        BigDecimal rechargeRate = discountCalculator.rechargeDiscountRate(userId);
        int qty = req.getQuantity();
        long total = product.getPrice() * qty;
        long discountedPrice = roundCents(BigDecimal.valueOf(product.getPrice()).multiply(memberRate).multiply(rechargeRate));
        long discountedSubtotal = discountedPrice * qty;
        long afterMember = roundCents(BigDecimal.valueOf(total).multiply(memberRate));
        long memberDiscount = total - afterMember;
        long rechargeDiscount = total - memberDiscount - discountedSubtotal;
        long deliveryFee = deliveryFee(req.getDeliveryType());

        // 关联订单（order_type=2 正餐）
        OrdOrder order = new OrdOrder();
        order.setOrderNo(generateNo("MO"));
        order.setUserId(userId);
        order.setEnterpriseId(user.getEnterpriseId());
        order.setOrderType(2);
        order.setOrderStatus(STATUS_PENDING);
        order.setTotalAmount(total);
        order.setDiscountAmount(total - discountedSubtotal);
        order.setMemberDiscount(memberDiscount);
        order.setRechargeDiscount(rechargeDiscount);
        order.setPayableAmount(discountedSubtotal + deliveryFee);
        order.setPaymentMethod(1);
        order.setDeliveryType(req.getDeliveryType());
        order.setDeliveryFee(deliveryFee);
        order.setDeliveryAddress(req.getDeliveryAddress());
        order.setReservationDate(req.getMenuDate());
        order.setReservationTime(slotToTime(req.getTimeSlot()));
        order.setRemark(req.getRemark());
        orderMapper.insert(order);

        // 预订 + 明细（当天菜单菜品快照）
        OrdMealReservation reservation = new OrdMealReservation();
        reservation.setReservationNo(generateNo("RS"));
        reservation.setUserId(userId);
        reservation.setEnterpriseId(user.getEnterpriseId());
        reservation.setOrderId(order.getId());
        reservation.setProductId(product.getId());
        reservation.setProductName(product.getProductName());
        reservation.setMenuDate(req.getMenuDate());
        reservation.setReservationDate(LocalDate.now());
        reservation.setReservationTimeSlot(req.getTimeSlot());
        reservation.setQuantity(qty);
        reservation.setDeliveryType(req.getDeliveryType());
        reservation.setDeliveryFee(deliveryFee);
        reservation.setDeliveryAddress(req.getDeliveryAddress());
        reservation.setTotalAmount(total);
        reservation.setDiscountAmount(total - discountedSubtotal);
        reservation.setPayableAmount(discountedSubtotal + deliveryFee);
        reservation.setPaymentMethod(1);
        reservation.setOutTradeNo(order.getOutTradeNo());
        reservation.setStatus(STATUS_PENDING);
        reservation.setRemark(req.getRemark());
        reservationMapper.insert(reservation);

        OrdMealReservationItem item = new OrdMealReservationItem();
        item.setReservationId(reservation.getId());
        item.setProductId(product.getId());
        item.setProductName(product.getProductName());
        item.setProductPrice(product.getPrice());
        item.setQuantity(qty);
        item.setSubtotal(total);
        item.setDiscountedPrice(discountedPrice);
        item.setDiscountedSubtotal(discountedSubtotal);
        item.setDishDetails(menuSnapshotJson(req.getMenuDate(), req.getProductId()));
        itemMapper.insert(item);

        // 1.2：初始状态留痕（待支付）
        statusHistoryService.record(OrderStatusHistoryService.BIZ_MEAL_RESERVATION,
                reservation.getId(), null, STATUS_PENDING, userId,
                OrderStatusHistoryService.OPERATOR_USER, "预订下单");

        return toVO(reservation, List.of(item));
    }

    /**
     * 余额支付：扣款（赠送余额优先）→ 乐观锁定预订 0→1（防并发重复支付，失败时本事务回滚扣款）→
     * 创建并结算支付单（trd_payments，幂等）→ 同步关联订单 → 状态历史。全程同一事务。
     */
    @Transactional
    public MealReservationVO pay(Long userId, Long reservationId) {
        OrdMealReservation reservation = requireOwn(userId, reservationId);
        if (reservation.getStatus() == null || reservation.getStatus() != STATUS_PENDING) {
            throw BizException.conflict("预订已处理");
        }
        // 1. 余额扣款（余额不足时抛异常，此时未产生任何写操作，预订保持待支付可重试）
        balanceService.debit(userId, reservation.getPayableAmount(), reservation.getOrderId(), "正餐预订");
        // 2. 乐观锁定预订 0→1（并发重复支付只有一个成功；锁定失败抛异常，本事务回滚扣款）
        LocalDateTime paidAt = LocalDateTime.now();
        String outTradeNo = generateNo("PO");
        int updated = reservationMapper.update(null, new LambdaUpdateWrapper<OrdMealReservation>()
                .eq(OrdMealReservation::getId, reservationId)
                .eq(OrdMealReservation::getStatus, STATUS_PENDING)
                .set(OrdMealReservation::getStatus, STATUS_READY)
                .set(OrdMealReservation::getPaidAt, paidAt)
                .set(OrdMealReservation::getOutTradeNo, outTradeNo));
        if (updated == 0) {
            throw BizException.conflict("预订已处理");
        }
        // 3. 创建支付单并立即结算（余额支付即时成功）
        TrdPayment payment = paymentService.create(userId, PaymentService.BIZ_MEAL_RESERVATION,
                reservationId, reservation.getPayableAmount(), PaymentService.METHOD_BALANCE,
                PaymentService.CHANNEL_BALANCE, outTradeNo);
        paymentService.settle(payment.getId(), null);
        // 4. 同步关联订单状态 + 状态历史
        syncOrderStatus(reservation.getOrderId(), STATUS_READY);
        statusHistoryService.record(OrderStatusHistoryService.BIZ_MEAL_RESERVATION,
                reservationId, STATUS_PENDING, STATUS_READY, userId,
                OrderStatusHistoryService.OPERATOR_USER, "余额支付");
        reservation.setStatus(STATUS_READY);
        reservation.setPaidAt(paidAt);
        reservation.setOutTradeNo(outTradeNo);
        return toVO(reservation, itemsOf(reservation.getId()));
    }

    /** 取消：待支付直接取消；已支付取消原路退款（退款单 + 状态历史） */
    @Transactional
    public MealReservationVO cancel(Long userId, Long reservationId, String reason) {
        OrdMealReservation reservation = requireOwn(userId, reservationId);
        int status = reservation.getStatus() == null ? STATUS_PENDING : reservation.getStatus();
        if (status == STATUS_COMPLETED || status == STATUS_CANCELLED || status == STATUS_REFUNDED) {
            throw BizException.conflict("预订已结束");
        }
        if (status == STATUS_MAKING) {
            throw BizException.conflict("预订备餐中，暂不可取消");
        }
        if (status == STATUS_READY) {
            // 已支付 → 原路退款（生成退款单 trd_refunds，幂等键防重复退款）
            refundReservationPayment(reservation, "预订取消退款",
                    "MEAL_CANCEL_REFUND:" + reservationId);
        }
        reservation.setStatus(STATUS_CANCELLED);
        reservation.setCancelledAt(LocalDateTime.now());
        reservation.setCancelReason(reason);
        reservationMapper.updateById(reservation);
        syncOrderStatus(reservation.getOrderId(), STATUS_CANCELLED);
        // 1.2：状态历史
        statusHistoryService.record(OrderStatusHistoryService.BIZ_MEAL_RESERVATION,
                reservationId, status, STATUS_CANCELLED, userId,
                OrderStatusHistoryService.OPERATOR_USER,
                reason == null || reason.isBlank() ? "用户取消" : reason);
        return toVO(reservation, itemsOf(reservation.getId()));
    }

    // ==================== 商家：备餐流转 ====================

    /** 备餐状态流转：1→2→3；1/2→5（退款，退款单 + 状态历史）；同步关联订单 */
    @Transactional
    public MealReservationVO adminUpdateStatus(Long reservationId, Integer target, Long operatorId) {
        OrdMealReservation reservation = require(reservationId);
        int cur = reservation.getStatus() == null ? STATUS_PENDING : reservation.getStatus();
        boolean valid = switch (target) {
            case STATUS_MAKING -> cur == STATUS_READY;
            case STATUS_COMPLETED -> cur == STATUS_MAKING;
            case STATUS_REFUNDED -> cur == STATUS_READY || cur == STATUS_MAKING;
            default -> false;
        };
        if (!valid) {
            throw BizException.conflict("非法的状态流转：" + cur + " → " + target);
        }
        if (target == STATUS_REFUNDED) {
            // 退款原路退回（生成退款单 trd_refunds，幂等键防重复退款）
            refundReservationPayment(reservation, "商家退款",
                    "MEAL_ADMIN_REFUND:" + reservationId);
        }
        reservation.setStatus(target);
        if (target == STATUS_COMPLETED) {
            reservation.setCompletedAt(LocalDateTime.now());
        }
        reservationMapper.updateById(reservation);
        syncOrderStatus(reservation.getOrderId(), target);
        // 1.2：状态历史
        String reason = switch (target) {
            case STATUS_MAKING -> "商家开始备餐";
            case STATUS_COMPLETED -> "备餐完成";
            case STATUS_REFUNDED -> "商家退款";
            default -> "状态变更";
        };
        statusHistoryService.record(OrderStatusHistoryService.BIZ_MEAL_RESERVATION,
                reservationId, cur, target, operatorId,
                OrderStatusHistoryService.OPERATOR_ADMIN, reason);
        return toVO(reservation, itemsOf(reservation.getId()));
    }

    /**
     * 预订原路退款：优先走退款单（trd_refunds，校验可退金额 + 幂等）；
     * 1.2 之前的历史预订无支付单，回退为 1.1 直接余额入账。
     */
    private void refundReservationPayment(OrdMealReservation reservation, String reason,
                                          String idempotencyKey) {
        TrdPayment payment = paymentService.getByBiz(PaymentService.BIZ_MEAL_RESERVATION,
                reservation.getId());
        if (payment == null) {
            balanceService.credit(reservation.getUserId(), reservation.getPayableAmount(), 0L,
                    BalanceService.TX_REFUND, reservation.getOrderId(), null, reason);
            return;
        }
        refundService.refundToBalance(reservation.getUserId(), payment.getId(),
                reservation.getPayableAmount(), reason, idempotencyKey, reservation.getOrderId());
    }

    // ==================== 查询 ====================

    public PageResult<MealReservationVO> myReservations(Long userId, int page, int size, Integer status) {
        Page<OrdMealReservation> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 1000));
        reservationMapper.selectPage(p, new LambdaQueryWrapper<OrdMealReservation>()
                .eq(OrdMealReservation::getUserId, userId)
                .eq(status != null, OrdMealReservation::getStatus, status)
                .orderByDesc(OrdMealReservation::getId));
        return PageResult.of(p.getTotal(), withItems(p.getRecords()));
    }

    public MealReservationVO getMine(Long userId, Long reservationId) {
        return toVO(requireOwn(userId, reservationId), itemsOf(reservationId));
    }

    public PageResult<MealReservationVO> adminPage(int page, int size, LocalDate date, Integer status) {
        Page<OrdMealReservation> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 1000));
        reservationMapper.selectPage(p, new LambdaQueryWrapper<OrdMealReservation>()
                .eq(date != null, OrdMealReservation::getMenuDate, date)
                .eq(status != null, OrdMealReservation::getStatus, status)
                .orderByDesc(OrdMealReservation::getId));
        return PageResult.of(p.getTotal(), withItems(p.getRecords()));
    }

    public MealReservationVO adminGet(Long reservationId) {
        return toVO(require(reservationId), itemsOf(reservationId));
    }

    // ==================== 规则 / 内部 ====================

    /** 预订窗口：提前 1 天；晚 8 点后最早后天；最远未来 3 天 */
    private void validateBookingWindow(LocalDate menuDate) {
        LocalDate today = LocalDate.now();
        LocalDate minDate = today.plusDays(1);
        if (LocalTime.now().isAfter(LocalTime.of(20, 0))) {
            minDate = today.plusDays(2);
        }
        LocalDate maxDate = today.plusDays(3);
        if (menuDate.isBefore(minDate) || menuDate.isAfter(maxDate)) {
            throw BizException.badRequest("可预订日期为 " + minDate + " ~ " + maxDate);
        }
    }

    private long deliveryFee(Integer deliveryType) {
        return deliveryType != null && deliveryType == DELIVERY_SURROUNDING
                ? SURROUNDING_DELIVERY_FEE : DELIVERY_ZERO;
    }

    /** 金额（分）乘折扣率后舍入到整数分（HALF_UP） */
    private static long roundCents(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /** 时段 → ord_orders.reservation_time（午餐 11:30 / 晚餐 17:30） */
    private java.time.LocalTime slotToTime(String slot) {
        return "晚餐".equals(slot) ? java.time.LocalTime.of(17, 30) : java.time.LocalTime.of(11, 30);
    }

    /** 当天菜单菜品快照 JSON */
    private String menuSnapshotJson(LocalDate menuDate, Long productId) {
        List<PrdDailyMenu> dishes = menuMapper.selectList(new LambdaQueryWrapper<PrdDailyMenu>()
                .eq(PrdDailyMenu::getMenuDate, menuDate)
                .eq(PrdDailyMenu::getProductId, productId)
                .eq(PrdDailyMenu::getIsAvailable, 1)
                .orderByAsc(PrdDailyMenu::getDishType)
                .orderByAsc(PrdDailyMenu::getSortOrder));
        List<Map<String, Object>> snapshot = dishes.stream().map(d -> Map.<String, Object>of(
                "dishName", d.getDishName(),
                "dishType", d.getDishType(),
                "sortOrder", d.getSortOrder())).toList();
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            return "[]";
        }
    }

    private void syncOrderStatus(Long orderId, int status) {
        if (orderId == null) {
            return;
        }
        OrdOrder order = orderMapper.selectById(orderId);
        if (order != null) {
            order.setOrderStatus(status);
            orderMapper.updateById(order);
        }
    }

    private String generateNo(String prefix) {
        return prefix + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + new java.security.SecureRandom().nextInt(1000, 10000);
    }

    private OrdMealReservation requireOwn(Long userId, Long reservationId) {
        OrdMealReservation reservation = reservationMapper.selectById(reservationId);
        if (reservation == null || !reservation.getUserId().equals(userId)) {
            throw BizException.notFound("预订不存在");
        }
        return reservation;
    }

    private OrdMealReservation require(Long reservationId) {
        OrdMealReservation reservation = reservationMapper.selectById(reservationId);
        if (reservation == null) {
            throw BizException.notFound("预订不存在");
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

    private List<OrdMealReservationItem> itemsOf(Long reservationId) {
        return itemMapper.selectList(new LambdaQueryWrapper<OrdMealReservationItem>()
                .eq(OrdMealReservationItem::getReservationId, reservationId));
    }

    private List<MealReservationVO> withItems(List<OrdMealReservation> reservations) {
        if (reservations.isEmpty()) {
            return List.of();
        }
        List<Long> ids = reservations.stream().map(OrdMealReservation::getId).toList();
        Map<Long, List<OrdMealReservationItem>> byReservation = itemMapper.selectList(
                        new LambdaQueryWrapper<OrdMealReservationItem>().in(OrdMealReservationItem::getReservationId, ids))
                .stream().collect(Collectors.groupingBy(OrdMealReservationItem::getReservationId));
        return reservations.stream()
                .map(r -> toVO(r, byReservation.getOrDefault(r.getId(), List.of())))
                .toList();
    }

    private MealReservationVO toVO(OrdMealReservation r, List<OrdMealReservationItem> items) {
        MealReservationVO vo = new MealReservationVO();
        vo.setId(r.getId());
        vo.setReservationNo(r.getReservationNo());
        vo.setOrderId(r.getOrderId());
        vo.setProductId(r.getProductId());
        vo.setProductName(r.getProductName());
        vo.setMenuDate(r.getMenuDate());
        vo.setReservationTimeSlot(r.getReservationTimeSlot());
        vo.setQuantity(r.getQuantity());
        vo.setDeliveryType(r.getDeliveryType());
        vo.setDeliveryFee(r.getDeliveryFee());
        vo.setDeliveryAddress(r.getDeliveryAddress());
        vo.setTotalAmount(r.getTotalAmount());
        vo.setDiscountAmount(r.getDiscountAmount());
        vo.setPayableAmount(r.getPayableAmount());
        vo.setStatus(r.getStatus());
        vo.setRemark(r.getRemark());
        vo.setPaidAt(TimeUtil.toEpochMillis(r.getPaidAt()));
        vo.setCompletedAt(TimeUtil.toEpochMillis(r.getCompletedAt()));
        vo.setCancelledAt(TimeUtil.toEpochMillis(r.getCancelledAt()));
        vo.setCancelReason(r.getCancelReason());
        vo.setCreatedAt(TimeUtil.toEpochMillis(r.getCreatedAt()));
        vo.setItems(items.stream().map(this::toItemVO).toList());
        vo.setDiscountedPrice(items.isEmpty() ? null : items.get(0).getDiscountedPrice());
        return vo;
    }

    private MealReservationItemVO toItemVO(OrdMealReservationItem item) {
        MealReservationItemVO vo = new MealReservationItemVO();
        vo.setProductId(item.getProductId());
        vo.setProductName(item.getProductName());
        vo.setProductPrice(item.getProductPrice());
        vo.setQuantity(item.getQuantity());
        vo.setSubtotal(item.getSubtotal());
        vo.setDiscountedPrice(item.getDiscountedPrice());
        vo.setDiscountedSubtotal(item.getDiscountedSubtotal());
        try {
            vo.setDishDetails(objectMapper.readTree(item.getDishDetails()));
        } catch (Exception e) {
            vo.setDishDetails(null);
        }
        return vo;
    }
}
