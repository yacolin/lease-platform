package com.example.leaseplatform.ord.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.common.TimeUtil;
import com.example.leaseplatform.ord.dto.OrderCreateReq;
import com.example.leaseplatform.ord.dto.OrderItemReq;
import com.example.leaseplatform.ord.dto.OrderItemVO;
import com.example.leaseplatform.ord.dto.OrderStatsVO;
import com.example.leaseplatform.ord.dto.OrderVO;
import com.example.leaseplatform.ord.entity.OrdOrder;
import com.example.leaseplatform.ord.entity.OrdOrderItem;
import com.example.leaseplatform.ord.mapper.OrdOrderItemMapper;
import com.example.leaseplatform.ord.mapper.OrdOrderMapper;
import com.example.leaseplatform.prd.entity.PrdProduct;
import com.example.leaseplatform.prd.entity.PrdSku;
import com.example.leaseplatform.prd.mapper.PrdProductMapper;
import com.example.leaseplatform.mkt.service.MktUserCouponService;
import com.example.leaseplatform.prd.service.PrdSkuService;
import com.example.leaseplatform.trd.entity.TrdPayment;
import com.example.leaseplatform.trd.service.AccountService;
import com.example.leaseplatform.trd.service.PaymentService;
import com.example.leaseplatform.trd.service.RefundService;
import com.example.leaseplatform.usr.entity.UsrUser;
import com.example.leaseplatform.usr.mapper.UsrUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 咖啡点单服务：
 * - 下单：商品+规格（spec_options）快照 → ord_orders + ord_order_items（待支付）；
 * - 折扣叠加：应付 = 原价 × 会员折扣率 × 充值赠送折扣率
 *   （member_discount：按 usr_member_levels.discount_rate；
 *    recharge_discount：按用户最近一次成功充值档位的 equivalent_discount，未充值不打折）；
 * - 余额支付：AccountService.debit（赠送余额优先扣）→ 待取餐 + 生成取餐码；
 * - 状态流转：待支付→待取餐→制作中→完成；取消（退款）/ 商家退款；取餐码核销；
 * - 查询：我的订单 / 商家后台分页筛选 + 统计。
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    /** 订单状态 */
    public static final int STATUS_PENDING = 0;   // 待支付
    public static final int STATUS_PICKUP = 1;    // 待取餐
    public static final int STATUS_MAKING = 2;    // 制作中
    public static final int STATUS_COMPLETED = 3; // 已完成
    public static final int STATUS_CANCELLED = 4; // 已取消
    public static final int STATUS_REFUNDED = 5;  // 已退款

    /** 订单类型 */
    public static final int TYPE_COFFEE = 1;

    /** 取餐码随机空间：6 位十进制（000000~999999） */
    private static final int PICKUP_CODE_SPACE = 1_000_000;

    /** 取餐码撞唯一索引后的最大换码重试次数 */
    private static final int PICKUP_CODE_MAX_ATTEMPTS = 5;

    /** 取餐码随机源（SecureRandom 线程安全，复用实例避免重复初始化开销） */
    private static final SecureRandom PICKUP_CODE_RANDOM = new SecureRandom();

    private final OrdOrderMapper orderMapper;
    private final OrdOrderItemMapper itemMapper;
    private final PrdProductMapper productMapper;
    private final UsrUserMapper userMapper;
    private final DiscountCalculator discountCalculator;
    private final AccountService accountService;
    private final PaymentService paymentService;
    private final RefundService refundService;
    private final OrderStatusHistoryService statusHistoryService;
    private final PrdSkuService skuService;
    private final MktUserCouponService couponService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 下单 ====================

    /** 咖啡下单：创建待支付订单 + 明细（折扣叠加计算） */
    @Transactional
    public OrderVO create(Long userId, OrderCreateReq req) {
        UsrUser user = requireUser(userId);
        // 计算会员折扣率与充值折扣率（叠加）
        BigDecimal memberRate = discountCalculator.memberDiscountRate(user);
        BigDecimal rechargeRate = discountCalculator.rechargeDiscountRate(userId);

        // 明细快照 + 原价（金额一律整数「分」，仅折扣率用 BigDecimal 参与乘法后舍入到分）
        List<OrdOrderItem> items = new ArrayList<>();
        long total = 0;
        for (OrderItemReq itemReq : req.getItems()) {
            PrdProduct product = productMapper.selectById(itemReq.getProductId());
            if (product == null || product.getIsAvailable() == null || product.getIsAvailable() != 1
                    || (product.getProductStatus() != null && product.getProductStatus() != 2)) {
                throw BizException.badRequest("商品已下架或不存在：" + itemReq.getProductId());
            }
            // 1.3：SKU 解析（指定 SKU 必须属于该商品且可售；未指定取默认 SKU，无 SKU 回落 SPU 价格）
            PrdSku sku = skuService.resolveForOrder(product.getId(), itemReq.getSkuId());
            long price = sku != null ? sku.getPrice() : product.getPrice();
            int qty = itemReq.getQuantity() == null ? 1 : itemReq.getQuantity();
            long subtotal = price * qty;
            // 折后单价（分，HALF_UP 舍入到分）
            long discountedPrice = roundCents(BigDecimal.valueOf(price).multiply(memberRate).multiply(rechargeRate));
            OrdOrderItem item = new OrdOrderItem();
            item.setProductId(product.getId());
            item.setSkuId(sku != null ? sku.getId() : null);
            item.setSkuNameSnapshot(sku != null ? sku.getSkuCode() : null);
            item.setSkuPriceSnapshot(sku != null ? sku.getPrice() : null);
            item.setSpecificationSnapshot(sku != null ? sku.getSpecSnapshot() : null);
            item.setProductName(product.getProductName());
            item.setProductPrice(price);
            item.setSpecification(toJson(itemReq.getSpec()));
            item.setQuantity(qty);
            item.setSubtotal(subtotal);
            item.setDiscountedPrice(discountedPrice);
            item.setDiscountedSubtotal(discountedPrice * qty);
            items.add(item);
            total += subtotal;
        }

        // 折扣金额拆分（应付 = 原价 − 会员折扣 − 充值折扣 − 优惠券，1.6）
        long afterMember = roundCents(BigDecimal.valueOf(total).multiply(memberRate));
        long memberDiscount = total - afterMember;
        long payableBeforeCoupon = items.stream().mapToLong(OrdOrderItem::getDiscountedSubtotal).sum();
        long rechargeDiscount = total - memberDiscount - payableBeforeCoupon;
        // 1.6 优惠券：在会员×充值折后金额上再抵扣（校验业务/门槛/指定商品与分类）
        long couponDiscount = 0;
        Long couponId = null;
        String couponNameSnapshot = null;
        if (req.getCouponId() != null) {
            List<Long> productIds = items.stream().map(OrdOrderItem::getProductId).toList();
            List<Long> categoryIds = productMapper.selectBatchIds(productIds).stream()
                    .map(PrdProduct::getCategoryId).distinct().toList();
            MktUserCouponService.CouponApplyResult coupon = couponService.apply(
                    userId, req.getCouponId(), MktUserCouponService.BIZ_COFFEE, payableBeforeCoupon,
                    productIds, categoryIds);
            couponDiscount = coupon.discount();
            couponId = coupon.userCoupon().getId();
            couponNameSnapshot = coupon.userCoupon().getCouponName();
        }
        long payable = payableBeforeCoupon - couponDiscount;

        OrdOrder order = new OrdOrder();
        order.setOrderNo(generateNo("CO"));
        order.setUserId(userId);
        order.setEnterpriseId(user.getEnterpriseId());
        order.setOrderType(TYPE_COFFEE);
        order.setOrderStatus(STATUS_PENDING);
        order.setTotalAmount(total);
        order.setDiscountAmount(total - payable);
        order.setMemberDiscount(memberDiscount);
        order.setRechargeDiscount(rechargeDiscount);
        order.setCouponId(couponId);
        order.setCouponNameSnapshot(couponNameSnapshot);
        order.setCouponDiscount(couponDiscount);
        order.setPayableAmount(payable);
        order.setPaymentMethod(req.getPaymentMethod() == null ? 1 : req.getPaymentMethod());
        order.setRemark(req.getRemark());
        orderMapper.insert(order);

        for (OrdOrderItem item : items) {
            item.setOrderId(order.getId());
            itemMapper.insert(item);
        }
        // 1.6 标记优惠券已使用（乐观 0→1；并发重复使用仅一次成功，失败整体回滚）
        if (couponId != null && !couponService.use(couponId, order.getId())) {
            throw BizException.conflict("优惠券已使用");
        }
        // 1.2：初始状态留痕（待支付）
        statusHistoryService.record(OrderStatusHistoryService.BIZ_COFFEE_ORDER, order.getId(),
                null, STATUS_PENDING, userId, OrderStatusHistoryService.OPERATOR_USER, "下单");
        return toVO(order, items);
    }

    // ==================== 余额支付 / 取消（退款） ====================

    /**
     * 余额支付：扣款（赠送余额优先）→ 乐观锁定订单 0→1（防并发重复支付，失败时本事务回滚扣款）→
     * 创建并结算支付单（trd_payments，幂等）→ 状态历史。全程同一事务。
     */
    @Transactional
    public OrderVO pay(Long userId, Long orderId) {
        OrdOrder order = requireOwnOrder(userId, orderId);
        if (order.getOrderStatus() == null || order.getOrderStatus() != STATUS_PENDING) {
            throw BizException.conflict("订单已处理");
        }
        if (order.getPaymentMethod() == null || order.getPaymentMethod() != 1) {
            throw BizException.badRequest("当前仅支持余额支付");
        }
        // 1. 余额扣款（余额不足时抛异常，此时未产生任何写操作，订单保持待支付可重试）
        accountService.debit(userId, order.getPayableAmount(), orderId, "咖啡订单");
        // 2. 乐观锁定订单 0→1（并发重复支付只有一个成功；锁定失败抛异常，本事务回滚扣款）
        LocalDateTime paidAt = LocalDateTime.now();
        String outTradeNo = generateNo("PO");
        // 取餐码不再「先查重再写入」（原实现最多 10 次 SELECT COUNT，且预检本身有 TOCTOU 竞态，
        // 真正保证唯一的是 uk_pickup_code 唯一索引）。改为直接写入，撞唯一索引时换码重试。
        int updated = 0;
        boolean codeCollision = false;
        String pickupCode = null;
        for (int attempt = 0; attempt < PICKUP_CODE_MAX_ATTEMPTS; attempt++) {
            String candidate = randomPickupCode();
            try {
                updated = orderMapper.update(null, new LambdaUpdateWrapper<OrdOrder>()
                        .eq(OrdOrder::getId, orderId)
                        .eq(OrdOrder::getOrderStatus, STATUS_PENDING)
                        .set(OrdOrder::getOrderStatus, STATUS_PICKUP)
                        .set(OrdOrder::getPaidAt, paidAt)
                        .set(OrdOrder::getPickupCode, candidate)
                        .set(OrdOrder::getOutTradeNo, outTradeNo));
                pickupCode = candidate;
                codeCollision = false;
                break; // 语句执行成功：0 行表示乐观锁失败（订单已被处理），不再重试
            } catch (DuplicateKeyException e) {
                // 仅可能来自 uk_pickup_code（本次 UPDATE 只改了这一个唯一列）→ 换码重试
                codeCollision = true;
            }
        }
        if (codeCollision) {
            throw BizException.conflict("取餐码生成失败，请重试");
        }
        if (updated == 0) {
            throw BizException.conflict("订单已处理");
        }
        // 3. 创建支付单并立即结算（余额支付即时成功）
        TrdPayment payment = paymentService.create(userId, PaymentService.BIZ_ORDER, orderId,
                order.getPayableAmount(), PaymentService.METHOD_BALANCE, PaymentService.CHANNEL_BALANCE,
                outTradeNo);
        paymentService.settle(payment.getId(), null);
        // 4. 状态历史
        statusHistoryService.record(OrderStatusHistoryService.BIZ_COFFEE_ORDER, orderId,
                STATUS_PENDING, STATUS_PICKUP, userId, OrderStatusHistoryService.OPERATOR_USER, "余额支付");
        order.setOrderStatus(STATUS_PICKUP);
        order.setPaidAt(paidAt);
        order.setPickupCode(pickupCode);
        order.setOutTradeNo(outTradeNo);
        return toVO(order, itemsOf(order.getId()));
    }

    /** 取消订单：待支付直接取消；待取餐取消并原路退款（退款单 + 状态历史） */
    @Transactional
    public OrderVO cancel(Long userId, Long orderId, String reason) {
        OrdOrder order = requireOwnOrder(userId, orderId);
        int status = order.getOrderStatus();
        if (status == STATUS_COMPLETED || status == STATUS_CANCELLED || status == STATUS_REFUNDED) {
            throw BizException.conflict("订单已结束");
        }
        if (status == STATUS_MAKING) {
            throw BizException.conflict("订单制作中，暂不可取消");
        }
        if (status == STATUS_PICKUP) {
            // 已支付 → 原路退款（生成退款单 trd_refunds，幂等键防重复退款）
            refundOrderPayment(order, "订单取消退款", "ORDER_CANCEL_REFUND:" + orderId);
        }
        order.setOrderStatus(STATUS_CANCELLED);
        order.setCancelledAt(LocalDateTime.now());
        order.setCancelReason(reason);
        orderMapper.updateById(order);
        // 1.2：状态历史
        statusHistoryService.record(OrderStatusHistoryService.BIZ_COFFEE_ORDER, orderId,
                status, STATUS_CANCELLED, userId, OrderStatusHistoryService.OPERATOR_USER,
                reason == null || reason.isBlank() ? "用户取消" : reason);
        return toVO(order, itemsOf(order.getId()));
    }

    /**
     * 订单原路退款：优先走退款单（trd_refunds，校验可退金额 + 幂等）；
     * 1.2 之前的历史订单无支付单，回退为 1.1 直接余额入账。
     */
    private void refundOrderPayment(OrdOrder order, String reason, String idempotencyKey) {
        TrdPayment payment = paymentService.getByBiz(PaymentService.BIZ_ORDER, order.getId());
        if (payment == null) {
            accountService.credit(order.getUserId(), order.getPayableAmount(), 0L,
                    AccountService.TX_REFUND, order.getId(), null, reason);
            return;
        }
        refundService.refundToBalance(order.getUserId(), payment.getId(), order.getPayableAmount(),
                reason, idempotencyKey, order.getId());
    }

    // ==================== 商家：状态推进 / 核销 / 退款 ====================

    /** 商家状态推进：1→2→3；1/2→5（退款原路退回，退款单 + 状态历史） */
    @Transactional
    public OrderVO adminUpdateStatus(Long orderId, Integer target, Long operatorId) {
        OrdOrder order = requireOrder(orderId);
        int cur = order.getOrderStatus() == null ? STATUS_PENDING : order.getOrderStatus();
        boolean valid = switch (target) {
            case STATUS_MAKING -> cur == STATUS_PICKUP;          // 待取餐 → 制作中
            case STATUS_COMPLETED -> cur == STATUS_MAKING;       // 制作中 → 完成
            case STATUS_REFUNDED -> cur == STATUS_PICKUP || cur == STATUS_MAKING; // 退款
            default -> false;
        };
        if (!valid) {
            throw BizException.conflict("非法的状态流转：" + cur + " → " + target);
        }
        if (target == STATUS_REFUNDED) {
            // 退款原路退回（生成退款单 trd_refunds，幂等键防重复退款）
            refundOrderPayment(order, "商家退款", "ORDER_ADMIN_REFUND:" + orderId);
        }
        order.setOrderStatus(target);
        if (target == STATUS_COMPLETED) {
            order.setCompletedAt(LocalDateTime.now());
        }
        orderMapper.updateById(order);
        // 1.2：状态历史
        String reason = switch (target) {
            case STATUS_MAKING -> "商家开始制作";
            case STATUS_COMPLETED -> "制作完成";
            case STATUS_REFUNDED -> "商家退款";
            default -> "状态变更";
        };
        statusHistoryService.record(OrderStatusHistoryService.BIZ_COFFEE_ORDER, orderId,
                cur, target, operatorId, OrderStatusHistoryService.OPERATOR_ADMIN, reason);
        return toVO(order, itemsOf(order.getId()));
    }

    /** 取餐码核销：待取餐/制作中 → 已完成（商家操作） */
    @Transactional
    public OrderVO verifyPickup(String pickupCode, Long operatorId) {
        OrdOrder order = orderMapper.selectOne(new LambdaQueryWrapper<OrdOrder>()
                .eq(OrdOrder::getPickupCode, pickupCode));
        if (order == null) {
            throw BizException.notFound("取餐码不存在");
        }
        int cur = order.getOrderStatus() == null ? STATUS_PENDING : order.getOrderStatus();
        if (cur != STATUS_PICKUP && cur != STATUS_MAKING) {
            throw BizException.conflict("订单当前状态不可核销");
        }
        order.setOrderStatus(STATUS_COMPLETED);
        order.setCompletedAt(LocalDateTime.now());
        orderMapper.updateById(order);
        // 1.2：状态历史
        statusHistoryService.record(OrderStatusHistoryService.BIZ_COFFEE_ORDER, order.getId(),
                cur, STATUS_COMPLETED, operatorId, OrderStatusHistoryService.OPERATOR_ADMIN, "取餐码核销");
        return toVO(order, itemsOf(order.getId()));
    }

    // ==================== 查询 ====================

    /** 我的订单（分页，可选状态筛选） */
    public PageResult<OrderVO> myOrders(Long userId, int page, int size, Integer status) {
        Page<OrdOrder> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 1000));
        orderMapper.selectPage(p, new LambdaQueryWrapper<OrdOrder>()
                .eq(OrdOrder::getUserId, userId)
                .eq(status != null, OrdOrder::getOrderStatus, status)
                .orderByDesc(OrdOrder::getId));
        return PageResult.of(p.getTotal(), withItems(p.getRecords()));
    }

    /** 订单详情（本人） */
    public OrderVO getMine(Long userId, Long orderId) {
        return toVO(requireOwnOrder(userId, orderId), itemsOf(orderId));
    }

    /** 商家分页（orderNo/状态/日期筛选） */
    public PageResult<OrderVO> adminPage(int page, int size, String orderNo, Integer status, LocalDate date) {
        Page<OrdOrder> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 1000));
        orderMapper.selectPage(p, new LambdaQueryWrapper<OrdOrder>()
                .like(orderNo != null && !orderNo.isBlank(), OrdOrder::getOrderNo, orderNo)
                .eq(status != null, OrdOrder::getOrderStatus, status)
                .ge(date != null, OrdOrder::getCreatedAt, date == null ? null : date.atStartOfDay())
                .lt(date != null, OrdOrder::getCreatedAt, date == null ? null : date.plusDays(1).atStartOfDay())
                .orderByDesc(OrdOrder::getId));
        return PageResult.of(p.getTotal(), withItems(p.getRecords()));
    }

    /** 商家订单详情 */
    public OrderVO adminGet(Long orderId) {
        return toVO(requireOrder(orderId), itemsOf(orderId));
    }

    /**
     * 商家统计：今日订单/金额 + 各状态待处理数。
     *
     * <p>原实现为 6 条 SQL，其中「今日金额」是把当天全部订单**整行**捞进 JVM 再求和
     * （无 LIMIT，含 remark 等大字段），见 docs/缓存与查询效率评估.md §2.4。
     *
     * <p>现改为 2 条聚合 SQL：
     * <ol>
     *   <li>今日指标一次聚合取回：{@code COUNT(*) + SUM(payable_amount) + SUM(状态条件)}
     *       —— 4 个指标 1 次往返、只回传 1 行，不再搬运整行；</li>
     *   <li>待取餐/制作中为<b>全时段</b>运营队列（保持原语义），一次 {@code GROUP BY} 取回
     *       —— 走 idx_order_status 覆盖索引，1 次往返替代原先 2 次 COUNT。</li>
     * </ol>
     *
     * <p>注：此处用 {@link QueryWrapper} 的 {@code select(...)} + {@code selectMaps(...)}
     * 表达聚合，仍是 MyBatis-Plus 的 Mapper API（未引入 XML mapper 或 {@code @Select}），
     * 与「SQL 全部由条件构造器生成」的项目约定一致。
     */
    public OrderStatsVO stats() {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        OrderStatsVO vo = new OrderStatsVO();

        // ① 今日 4 个指标：1 条 SQL
        Map<String, Object> today = orderMapper.selectMaps(new QueryWrapper<OrdOrder>()
                        .select("COUNT(*) AS today_orders",
                                "IFNULL(SUM(payable_amount), 0) AS today_amount",
                                "IFNULL(SUM(order_status = " + STATUS_COMPLETED + "), 0) AS today_completed",
                                "IFNULL(SUM(order_status = " + STATUS_CANCELLED + "), 0) AS today_cancelled")
                        .ge("created_at", start)
                        .lt("created_at", end))
                .stream().findFirst().orElse(Map.of());
        vo.setTodayOrders(toLong(today.get("today_orders")));
        vo.setTodayAmount(toLong(today.get("today_amount")));
        vo.setTodayCompleted(toLong(today.get("today_completed")));
        vo.setTodayCancelled(toLong(today.get("today_cancelled")));

        // ② 全时段运营队列（待取餐 / 制作中）：1 条 GROUP BY
        Map<Integer, Long> queues = orderMapper.selectMaps(new QueryWrapper<OrdOrder>()
                        .select("order_status", "COUNT(*) AS cnt")
                        .in("order_status", STATUS_PICKUP, STATUS_MAKING)
                        .groupBy("order_status"))
                .stream()
                .collect(Collectors.toMap(
                        m -> ((Number) m.get("order_status")).intValue(),
                        m -> toLong(m.get("cnt"))));
        vo.setPendingPickupCount(queues.getOrDefault(STATUS_PICKUP, 0L));
        vo.setMakingCount(queues.getOrDefault(STATUS_MAKING, 0L));
        return vo;
    }

    /** 聚合结果取值：null / 非数值一律按 0 处理 */
    private static long toLong(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }

    // ==================== 内部工具 ====================

    /** 金额（分）乘折扣率后舍入到整数分（HALF_UP） */
    private static long roundCents(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /**
     * 生成 6 位随机取餐码（不查库）。
     *
     * <p>唯一性由 {@code ord_orders.uk_pickup_code} 唯一索引保证：调用方捕获
     * {@link DuplicateKeyException} 后换码重试。原先「先 SELECT COUNT 查重再写入」的做法
     * 不仅有 TOCTOU 竞态（预检通过后仍可能被并发插入抢占），还平白多 1~10 次查询。
     */
    String randomPickupCode() {
        return String.format("%06d", PICKUP_CODE_RANDOM.nextInt(PICKUP_CODE_SPACE));
    }

    private String generateNo(String prefix) {
        return prefix + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + new SecureRandom().nextInt(1000, 10000);
    }

    private String toJson(Map<String, Object> spec) {
        if (spec == null || spec.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(spec);
        } catch (Exception e) {
            throw BizException.badRequest("规格格式不正确");
        }
    }

    private OrdOrder requireOwnOrder(Long userId, Long orderId) {
        OrdOrder order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw BizException.notFound("订单不存在");
        }
        return order;
    }

    private OrdOrder requireOrder(Long orderId) {
        OrdOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw BizException.notFound("订单不存在");
        }
        return order;
    }

    private UsrUser requireUser(Long id) {
        UsrUser user = userMapper.selectById(id);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        return user;
    }

    private List<OrdOrderItem> itemsOf(Long orderId) {
        return itemMapper.selectList(new LambdaQueryWrapper<OrdOrderItem>()
                .eq(OrdOrderItem::getOrderId, orderId));
    }

    private List<OrderVO> withItems(List<OrdOrder> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        List<Long> orderIds = orders.stream().map(OrdOrder::getId).toList();
        Map<Long, List<OrdOrderItem>> itemsByOrder = itemMapper.selectList(
                        new LambdaQueryWrapper<OrdOrderItem>().in(OrdOrderItem::getOrderId, orderIds))
                .stream().collect(Collectors.groupingBy(OrdOrderItem::getOrderId));
        return orders.stream()
                .map(o -> toVO(o, itemsByOrder.getOrDefault(o.getId(), List.of())))
                .toList();
    }

    private OrderVO toVO(OrdOrder o, List<OrdOrderItem> items) {
        OrderVO vo = new OrderVO();
        vo.setId(o.getId());
        vo.setOrderNo(o.getOrderNo());
        vo.setEnterpriseId(o.getEnterpriseId());
        vo.setOrderType(o.getOrderType());
        vo.setOrderStatus(o.getOrderStatus());
        vo.setTotalAmount(o.getTotalAmount());
        vo.setDiscountAmount(o.getDiscountAmount());
        vo.setMemberDiscount(o.getMemberDiscount());
        vo.setRechargeDiscount(o.getRechargeDiscount());
        vo.setCouponId(o.getCouponId());
        vo.setCouponNameSnapshot(o.getCouponNameSnapshot());
        vo.setCouponDiscount(o.getCouponDiscount());
        vo.setPayableAmount(o.getPayableAmount());
        vo.setPaymentMethod(o.getPaymentMethod());
        vo.setPickupCode(o.getPickupCode());
        vo.setRemark(o.getRemark());
        vo.setPaidAt(TimeUtil.toEpochMillis(o.getPaidAt()));
        vo.setCompletedAt(TimeUtil.toEpochMillis(o.getCompletedAt()));
        vo.setCancelledAt(TimeUtil.toEpochMillis(o.getCancelledAt()));
        vo.setCancelReason(o.getCancelReason());
        vo.setCreatedAt(TimeUtil.toEpochMillis(o.getCreatedAt()));
        vo.setItems(items.stream().map(this::toItemVO).toList());
        return vo;
    }

    private OrderItemVO toItemVO(OrdOrderItem item) {
        OrderItemVO vo = new OrderItemVO();
        vo.setProductId(item.getProductId());
        vo.setSkuId(item.getSkuId());
        vo.setSkuNameSnapshot(item.getSkuNameSnapshot());
        vo.setSkuPriceSnapshot(item.getSkuPriceSnapshot());
        vo.setSpecificationSnapshot(parseJson(item.getSpecificationSnapshot()));
        vo.setProductName(item.getProductName());
        vo.setProductPrice(item.getProductPrice());
        vo.setSpecification(parseJson(item.getSpecification()));
        vo.setQuantity(item.getQuantity());
        vo.setSubtotal(item.getSubtotal());
        vo.setDiscountedPrice(item.getDiscountedPrice());
        vo.setDiscountedSubtotal(item.getDiscountedSubtotal());
        return vo;
    }

    private JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }
}
