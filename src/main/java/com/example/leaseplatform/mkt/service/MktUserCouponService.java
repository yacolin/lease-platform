package com.example.leaseplatform.mkt.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.common.TimeUtil;
import com.example.leaseplatform.mkt.dto.UserCouponVO;
import com.example.leaseplatform.mkt.entity.MktCoupon;
import com.example.leaseplatform.mkt.entity.MktUserCoupon;
import com.example.leaseplatform.mkt.mapper.MktUserCouponMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户优惠券服务（1.6）：领取（券规则快照）/ 我的券（惰性过期）/ 下单校验并计算优惠 / 标记使用。
 * <pre>
 *   优惠券模板（mkt_coupons，规则可改）
 *      ↓ 领取时快照
 *   用户券（mkt_user_coupons）
 *      ↓ 下单校验 + 计算优惠
 *   订单优惠（ord_orders.coupon_discount + 名称快照）
 * </pre>
 * 折扣叠加链（1.6.4 Promotion 基础）：应付 = 原价 × 会员折扣率 × 充值折扣率 − 优惠券。
 */
@Service
@RequiredArgsConstructor
public class MktUserCouponService {

    /** 适用业务 */
    public static final int BIZ_COFFEE = 1;
    public static final int BIZ_MEAL = 2;

    private final MktUserCouponMapper userCouponMapper;
    private final MktCouponService couponService;
    private final tools.jackson.databind.ObjectMapper objectMapper = new tools.jackson.databind.ObjectMapper();

    /** 领取：校验模板启用 + 未重复领取；按券规则生成用户券快照 */
    @Transactional
    public UserCouponVO claim(Long userId, Long couponId) {
        MktCoupon coupon = couponService.require(couponId);
        if (coupon.getStatus() == null || coupon.getStatus() != MktCoupon.STATUS_ENABLED) {
            throw BizException.badRequest("该优惠券已停用");
        }
        Long dup = userCouponMapper.selectCount(new LambdaQueryWrapper<MktUserCoupon>()
                .eq(MktUserCoupon::getCouponId, couponId)
                .eq(MktUserCoupon::getUserId, userId)
                .in(MktUserCoupon::getStatus, List.of(MktUserCoupon.STATUS_UNUSED, MktUserCoupon.STATUS_USED)));
        if (dup != null && dup > 0) {
            throw BizException.conflict("已领取过该优惠券");
        }
        MktUserCoupon uc = new MktUserCoupon();
        uc.setCouponId(coupon.getId());
        uc.setUserId(userId);
        uc.setCouponName(coupon.getCouponName());
        uc.setCouponType(coupon.getCouponType());
        uc.setDiscountAmount(coupon.getDiscountAmount());
        uc.setDiscountRate(coupon.getDiscountRate());
        uc.setThresholdAmount(coupon.getThresholdAmount() == null ? 0L : coupon.getThresholdAmount());
        uc.setBizType(coupon.getBizType());
        uc.setProductIds(coupon.getProductIds());
        uc.setCategoryIds(coupon.getCategoryIds());
        uc.setStatus(MktUserCoupon.STATUS_UNUSED);
        uc.setExpireAt(LocalDateTime.now().plusDays(coupon.getValidityDays() == null ? 30 : coupon.getValidityDays()));
        userCouponMapper.insert(uc);
        return toVO(uc);
    }

    /**
     * 我的优惠券（分页，可选状态筛选；惰性过期）。
     *
     * <p>本方法会先执行 {@code expireUnused} 写操作（惰性过期），再分页查询，
     * 因此必须加事务：否则写操作在 autocommit 下单独提交，与随后的查询不构成一致视图
     * （见 docs/缓存与查询效率评估.md §2.0 同类问题）。
     */
    @Transactional
    public PageResult<UserCouponVO> myCoupons(Long userId, int page, int size, Integer status) {
        expireUnused(userId);
        Page<MktUserCoupon> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 1000));
        userCouponMapper.selectPage(p, new LambdaQueryWrapper<MktUserCoupon>()
                .eq(MktUserCoupon::getUserId, userId)
                .eq(status != null, MktUserCoupon::getStatus, status)
                .orderByDesc(MktUserCoupon::getId));
        return PageResult.of(p.getTotal(), p.getRecords().stream().map(this::toVO).toList());
    }

    /**
     * 下单校验并计算优惠（只读，不改变状态）：
     * 校验归属/未使用/未过期/业务范围/门槛/指定商品与分类；
     * 满减 → min(满减金额, 折后金额)；折扣 → 折后金额 × (1 − 折扣率)。
     *
     * @param amountBeforeCoupon 折后金额（会员×充值折扣后、优惠券前）
     * @param productIds         订单商品 ID 列表
     * @param categoryIds        订单商品分类 ID 列表
     */
    @Transactional
    public CouponApplyResult apply(Long userId, Long userCouponId, int bizType,
                                   long amountBeforeCoupon, List<Long> productIds, List<Long> categoryIds) {
        MktUserCoupon uc = userCouponMapper.selectById(userCouponId);
        if (uc == null || !uc.getUserId().equals(userId)) {
            throw BizException.notFound("优惠券不存在");
        }
        if (uc.getStatus() == null || uc.getStatus() != MktUserCoupon.STATUS_UNUSED) {
            throw BizException.conflict("优惠券不可用");
        }
        if (uc.getExpireAt() != null && uc.getExpireAt().isBefore(LocalDateTime.now())) {
            markExpired(uc.getId());
            throw BizException.badRequest("优惠券已过期");
        }
        if (uc.getBizType() != null && uc.getBizType() != bizType) {
            throw BizException.badRequest("该优惠券不适用于此订单");
        }
        if (amountBeforeCoupon < (uc.getThresholdAmount() == null ? 0L : uc.getThresholdAmount())) {
            throw BizException.badRequest("未满足优惠券使用门槛");
        }
        if (uc.getProductIds() != null && !overlap(parseIds(uc.getProductIds()), productIds)) {
            throw BizException.badRequest("该优惠券不适用于所选商品");
        }
        if (uc.getCategoryIds() != null && !overlap(parseIds(uc.getCategoryIds()), categoryIds)) {
            throw BizException.badRequest("该优惠券不适用于所选分类");
        }
        long discount = switch (uc.getCouponType() == null ? 0 : uc.getCouponType()) {
            case MktCoupon.TYPE_FULL_REDUCTION ->
                    Math.min(uc.getDiscountAmount() == null ? 0L : uc.getDiscountAmount(), amountBeforeCoupon);
            case MktCoupon.TYPE_DISCOUNT ->
                    roundCents(BigDecimal.valueOf(amountBeforeCoupon)
                            .multiply(BigDecimal.ONE.subtract(uc.getDiscountRate() == null
                                    ? BigDecimal.ZERO : uc.getDiscountRate())));
            default -> 0L;
        };
        return new CouponApplyResult(uc, discount);
    }

    /** 标记使用（乐观 status 0→1 + 绑定订单）；返回是否本次变更（并发重复使用仅一次成功） */
    @Transactional
    public boolean use(Long userCouponId, Long orderId) {
        return userCouponMapper.update(null, new LambdaUpdateWrapper<MktUserCoupon>()
                .eq(MktUserCoupon::getId, userCouponId)
                .eq(MktUserCoupon::getStatus, MktUserCoupon.STATUS_UNUSED)
                .set(MktUserCoupon::getStatus, MktUserCoupon.STATUS_USED)
                .set(MktUserCoupon::getOrderId, orderId)
                .set(MktUserCoupon::getUsedAt, LocalDateTime.now())) > 0;
    }

    /** 惰性过期：未使用且已过期的券置为已过期 */
    private void expireUnused(Long userId) {
        userCouponMapper.update(null, new LambdaUpdateWrapper<MktUserCoupon>()
                .eq(MktUserCoupon::getUserId, userId)
                .eq(MktUserCoupon::getStatus, MktUserCoupon.STATUS_UNUSED)
                .lt(MktUserCoupon::getExpireAt, LocalDateTime.now())
                .set(MktUserCoupon::getStatus, MktUserCoupon.STATUS_EXPIRED));
    }

    private void markExpired(Long userCouponId) {
        userCouponMapper.update(null, new LambdaUpdateWrapper<MktUserCoupon>()
                .eq(MktUserCoupon::getId, userCouponId)
                .eq(MktUserCoupon::getStatus, MktUserCoupon.STATUS_UNUSED)
                .set(MktUserCoupon::getStatus, MktUserCoupon.STATUS_EXPIRED));
    }

    private boolean overlap(Set<Long> a, List<Long> b) {
        if (b == null || b.isEmpty()) {
            return false;
        }
        return b.stream().anyMatch(a::contains);
    }

    private Set<Long> parseIds(String json) {
        try {
            tools.jackson.databind.JsonNode node = objectMapper.readTree(json);
            if (node == null || !node.isArray()) {
                return Set.of();
            }
            Set<Long> ids = new java.util.HashSet<>();
            node.forEach(n -> ids.add(n.asLong()));
            return ids;
        } catch (Exception e) {
            return Set.of();
        }
    }

    private static long roundCents(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private UserCouponVO toVO(MktUserCoupon uc) {
        UserCouponVO vo = new UserCouponVO();
        vo.setId(uc.getId());
        vo.setCouponId(uc.getCouponId());
        vo.setCouponName(uc.getCouponName());
        vo.setCouponType(uc.getCouponType());
        vo.setDiscountAmount(uc.getDiscountAmount());
        vo.setDiscountRate(uc.getDiscountRate());
        vo.setThresholdAmount(uc.getThresholdAmount());
        vo.setBizType(uc.getBizType());
        vo.setStatus(uc.getStatus());
        vo.setOrderId(uc.getOrderId());
        vo.setExpireAt(TimeUtil.toEpochMillis(uc.getExpireAt()));
        vo.setCreatedAt(TimeUtil.toEpochMillis(uc.getCreatedAt()));
        return vo;
    }

    /** 优惠券校验结果：用户券 + 优惠金额（分） */
    public record CouponApplyResult(MktUserCoupon userCoupon, long discount) {
    }
}
