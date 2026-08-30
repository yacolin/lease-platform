package com.example.leaseplatform.ord.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.example.leaseplatform.prd.mapper.PrdProductMapper;
import com.example.leaseplatform.trd.service.BalanceService;
import com.example.leaseplatform.usr.entity.UsrUser;
import com.example.leaseplatform.usr.mapper.UsrUserMapper;
import lombok.RequiredArgsConstructor;
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
 * - 余额支付：BalanceService.debit（赠送余额优先扣）→ 待取餐 + 生成取餐码；
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

    private final OrdOrderMapper orderMapper;
    private final OrdOrderItemMapper itemMapper;
    private final PrdProductMapper productMapper;
    private final UsrUserMapper userMapper;
    private final DiscountCalculator discountCalculator;
    private final BalanceService balanceService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 下单 ====================

    /** 咖啡下单：创建待支付订单 + 明细（折扣叠加计算） */
    @Transactional
    public OrderVO create(Long userId, OrderCreateReq req) {
        UsrUser user = requireUser(userId);
        // 计算会员折扣率与充值折扣率（叠加）
        BigDecimal memberRate = discountCalculator.memberDiscountRate(user);
        BigDecimal rechargeRate = discountCalculator.rechargeDiscountRate(userId);

        // 明细快照 + 原价
        List<OrdOrderItem> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItemReq itemReq : req.getItems()) {
            PrdProduct product = productMapper.selectById(itemReq.getProductId());
            if (product == null || product.getIsAvailable() == null || product.getIsAvailable() != 1) {
                throw BizException.badRequest("商品已下架或不存在：" + itemReq.getProductId());
            }
            BigDecimal price = product.getPrice();
            int qty = itemReq.getQuantity() == null ? 1 : itemReq.getQuantity();
            BigDecimal subtotal = price.multiply(BigDecimal.valueOf(qty));
            // 折后单价（HALF_UP 到分）
            BigDecimal discountedPrice = price.multiply(memberRate).multiply(rechargeRate)
                    .setScale(2, RoundingMode.HALF_UP);
            OrdOrderItem item = new OrdOrderItem();
            item.setProductId(product.getId());
            item.setProductName(product.getProductName());
            item.setProductPrice(price);
            item.setSpecification(toJson(itemReq.getSpec()));
            item.setQuantity(qty);
            item.setSubtotal(subtotal);
            item.setDiscountedPrice(discountedPrice);
            item.setDiscountedSubtotal(discountedPrice.multiply(BigDecimal.valueOf(qty))
                    .setScale(2, RoundingMode.HALF_UP));
            items.add(item);
            total = total.add(subtotal);
        }

        // 折扣金额拆分（保证 payable = total - memberDiscount - rechargeDiscount 恒等）
        BigDecimal afterMember = total.multiply(memberRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal memberDiscount = total.subtract(afterMember);
        BigDecimal payable = items.stream()
                .map(OrdOrderItem::getDiscountedSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal rechargeDiscount = total.subtract(memberDiscount).subtract(payable);

        OrdOrder order = new OrdOrder();
        order.setOrderNo(generateNo("CO"));
        order.setUserId(userId);
        order.setEnterpriseId(user.getEnterpriseId());
        order.setOrderType(TYPE_COFFEE);
        order.setOrderStatus(STATUS_PENDING);
        order.setTotalAmount(total);
        order.setDiscountAmount(total.subtract(payable));
        order.setMemberDiscount(memberDiscount);
        order.setRechargeDiscount(rechargeDiscount);
        order.setPayableAmount(payable);
        order.setPaymentMethod(req.getPaymentMethod() == null ? 1 : req.getPaymentMethod());
        order.setRemark(req.getRemark());
        orderMapper.insert(order);

        for (OrdOrderItem item : items) {
            item.setOrderId(order.getId());
            itemMapper.insert(item);
        }
        return toVO(order, items);
    }

    // ==================== 余额支付 / 取消（退款） ====================

    /** 余额支付：扣款（赠送余额优先）→ 待取餐 + 取餐码 */
    @Transactional
    public OrderVO pay(Long userId, Long orderId) {
        OrdOrder order = requireOwnOrder(userId, orderId);
        if (order.getOrderStatus() == null || order.getOrderStatus() != STATUS_PENDING) {
            throw BizException.conflict("订单已处理");
        }
        if (order.getPaymentMethod() == null || order.getPaymentMethod() != 1) {
            throw BizException.badRequest("当前仅支持余额支付");
        }
        // 余额不足时 debit 抛"余额不足"，订单保持待支付可重试
        balanceService.debit(userId, order.getPayableAmount(), order.getId(), "咖啡订单");
        order.setOrderStatus(STATUS_PICKUP);
        order.setPaidAt(LocalDateTime.now());
        order.setPickupCode(generatePickupCode());
        orderMapper.updateById(order);
        return toVO(order, itemsOf(order.getId()));
    }

    /** 取消订单：待支付直接取消；待取餐取消并原路退款 */
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
            // 已支付 → 原路退款（余额流水 TX_REFUND）
            balanceService.credit(userId, order.getPayableAmount(), BigDecimal.ZERO,
                    BalanceService.TX_REFUND, order.getId(), null, "订单取消退款");
        }
        order.setOrderStatus(STATUS_CANCELLED);
        order.setCancelledAt(LocalDateTime.now());
        order.setCancelReason(reason);
        orderMapper.updateById(order);
        return toVO(order, itemsOf(order.getId()));
    }

    // ==================== 商家：状态推进 / 核销 / 退款 ====================

    /** 商家状态推进：1→2→3；1/2→5（退款原路退回） */
    @Transactional
    public OrderVO adminUpdateStatus(Long orderId, Integer target) {
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
            // 退款原路退回（余额流水 TX_REFUND）
            balanceService.credit(order.getUserId(), order.getPayableAmount(), BigDecimal.ZERO,
                    BalanceService.TX_REFUND, order.getId(), null, "商家退款");
        }
        order.setOrderStatus(target);
        if (target == STATUS_COMPLETED) {
            order.setCompletedAt(LocalDateTime.now());
        }
        orderMapper.updateById(order);
        return toVO(order, itemsOf(order.getId()));
    }

    /** 取餐码核销：待取餐/制作中 → 已完成 */
    @Transactional
    public OrderVO verifyPickup(String pickupCode) {
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

    /** 商家统计：今日订单/金额 + 各状态待处理数 */
    public OrderStatsVO stats() {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        OrderStatsVO vo = new OrderStatsVO();
        vo.setTodayOrders(orderMapper.selectCount(new LambdaQueryWrapper<OrdOrder>()
                .ge(OrdOrder::getCreatedAt, start).lt(OrdOrder::getCreatedAt, end)));
        List<OrdOrder> today = orderMapper.selectList(new LambdaQueryWrapper<OrdOrder>()
                .ge(OrdOrder::getCreatedAt, start).lt(OrdOrder::getCreatedAt, end));
        vo.setTodayAmount(today.stream()
                .map(o -> o.getPayableAmount() == null ? BigDecimal.ZERO : o.getPayableAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        vo.setPendingPickupCount(orderMapper.selectCount(new LambdaQueryWrapper<OrdOrder>()
                .eq(OrdOrder::getOrderStatus, STATUS_PICKUP)));
        vo.setMakingCount(orderMapper.selectCount(new LambdaQueryWrapper<OrdOrder>()
                .eq(OrdOrder::getOrderStatus, STATUS_MAKING)));
        vo.setTodayCompleted(countByStatusToday(start, end, STATUS_COMPLETED));
        vo.setTodayCancelled(countByStatusToday(start, end, STATUS_CANCELLED));
        return vo;
    }

    private Long countByStatusToday(LocalDateTime start, LocalDateTime end, int status) {
        return orderMapper.selectCount(new LambdaQueryWrapper<OrdOrder>()
                .ge(OrdOrder::getCreatedAt, start).lt(OrdOrder::getCreatedAt, end)
                .eq(OrdOrder::getOrderStatus, status));
    }

    // ==================== 内部工具 ====================

    private String generatePickupCode() {
        SecureRandom random = new SecureRandom();
        for (int i = 0; i < 10; i++) {
            String code = String.format("%06d", random.nextInt(1_000_000));
            Long dup = orderMapper.selectCount(new LambdaQueryWrapper<OrdOrder>()
                    .eq(OrdOrder::getPickupCode, code));
            if (dup == null || dup == 0) {
                return code;
            }
        }
        throw BizException.conflict("取餐码生成失败，请重试");
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
