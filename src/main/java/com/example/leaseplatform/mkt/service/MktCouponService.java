package com.example.leaseplatform.mkt.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.common.TimeUtil;
import com.example.leaseplatform.mkt.dto.CouponCreateReq;
import com.example.leaseplatform.mkt.dto.CouponUpdateReq;
import com.example.leaseplatform.mkt.dto.CouponVO;
import com.example.leaseplatform.mkt.entity.MktCoupon;
import com.example.leaseplatform.mkt.entity.MktUserCoupon;
import com.example.leaseplatform.mkt.mapper.MktCouponMapper;
import com.example.leaseplatform.mkt.mapper.MktUserCouponMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * 优惠券模板服务（1.6 运营/营销，管理端 CRUD + 公开可领列表）。
 * 券规则独立于订单：领取时由 {@link MktUserCouponService} 做快照，规则可改、用户券不变
 * （roadmap 1.6.2：优惠券 → 优惠规则 → 订单优惠 → 优惠快照）。
 */
@Service
@RequiredArgsConstructor
public class MktCouponService {

    private final MktCouponMapper couponMapper;
    private final MktUserCouponMapper userCouponMapper;
    private final ObjectMapper objectMapper;

    /** 管理端分页 */
    public PageResult<CouponVO> page(int page, int size, Integer couponType, Integer status, String keyword) {
        Page<MktCoupon> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 1000));
        couponMapper.selectPage(p, new LambdaQueryWrapper<MktCoupon>()
                .eq(couponType != null, MktCoupon::getCouponType, couponType)
                .eq(status != null, MktCoupon::getStatus, status)
                .like(keyword != null && !keyword.isBlank(), MktCoupon::getCouponName, keyword)
                .orderByAsc(MktCoupon::getSortOrder)
                .orderByAsc(MktCoupon::getId));
        return PageResult.of(p.getTotal(), p.getRecords().stream().map(this::toVO).toList());
    }

    /** 详情 */
    public CouponVO getById(Long id) {
        return toVO(require(id));
    }

    @Transactional
    public CouponVO create(CouponCreateReq req) {
        MktCoupon coupon = new MktCoupon();
        apply(coupon, req.getCouponName(), req.getCouponType(), req.getDiscountAmount(),
                req.getDiscountRate(), req.getThresholdAmount(), req.getBizType(),
                req.getProductIds(), req.getCategoryIds(), req.getValidityDays(),
                req.getStatus(), req.getSortOrder());
        couponMapper.insert(coupon);
        return toVO(coupon);
    }

    @Transactional
    public CouponVO update(Long id, CouponUpdateReq req) {
        MktCoupon coupon = require(id);
        apply(coupon, req.getCouponName(), req.getCouponType(), req.getDiscountAmount(),
                req.getDiscountRate(), req.getThresholdAmount(), req.getBizType(),
                req.getProductIds(), req.getCategoryIds(), req.getValidityDays(),
                req.getStatus(), req.getSortOrder());
        couponMapper.updateById(coupon);
        return toVO(coupon);
    }

    /** 删除：已被用户领取时拒绝（用户券保留快照） */
    @Transactional
    public void delete(Long id) {
        require(id);
        Long ref = userCouponMapper.selectCount(new LambdaQueryWrapper<MktUserCoupon>()
                .eq(MktUserCoupon::getCouponId, id));
        if (ref != null && ref > 0) {
            throw BizException.conflict("该优惠券已被用户领取，无法删除（可停用）");
        }
        couponMapper.deleteById(id);
    }

    /** 公开可领列表（仅启用） */
    public List<CouponVO> publicList() {
        return couponMapper.selectList(new LambdaQueryWrapper<MktCoupon>()
                        .eq(MktCoupon::getStatus, MktCoupon.STATUS_ENABLED)
                        .orderByAsc(MktCoupon::getSortOrder)
                        .orderByAsc(MktCoupon::getId))
                .stream().map(this::toVO).toList();
    }

    MktCoupon require(Long id) {
        MktCoupon coupon = couponMapper.selectById(id);
        if (coupon == null) {
            throw BizException.notFound("优惠券不存在");
        }
        return coupon;
    }

    private void apply(MktCoupon coupon, String name, Integer type, Long discountAmount,
                       java.math.BigDecimal discountRate, Long threshold, Integer bizType,
                       List<Long> productIds, List<Long> categoryIds, Integer validityDays,
                       Integer status, Integer sortOrder) {
        coupon.setCouponName(name);
        coupon.setCouponType(type);
        coupon.setDiscountAmount(discountAmount);
        coupon.setDiscountRate(discountRate);
        coupon.setThresholdAmount(threshold == null ? 0L : threshold);
        coupon.setBizType(bizType);
        coupon.setProductIds(writeIds(productIds));
        coupon.setCategoryIds(writeIds(categoryIds));
        coupon.setValidityDays(validityDays == null ? 30 : validityDays);
        coupon.setStatus(status == null ? MktCoupon.STATUS_ENABLED : status);
        coupon.setSortOrder(sortOrder == null ? 0 : sortOrder);
    }

    String writeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (Exception e) {
            throw BizException.badRequest("ID 列表格式不正确");
        }
    }

    Object readIds(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return json;
        }
    }

    private CouponVO toVO(MktCoupon c) {
        CouponVO vo = new CouponVO();
        vo.setId(c.getId());
        vo.setCouponName(c.getCouponName());
        vo.setCouponType(c.getCouponType());
        vo.setDiscountAmount(c.getDiscountAmount());
        vo.setDiscountRate(c.getDiscountRate());
        vo.setThresholdAmount(c.getThresholdAmount());
        vo.setBizType(c.getBizType());
        vo.setProductIds(readIds(c.getProductIds()));
        vo.setCategoryIds(readIds(c.getCategoryIds()));
        vo.setValidityDays(c.getValidityDays());
        vo.setStatus(c.getStatus());
        vo.setSortOrder(c.getSortOrder());
        vo.setCreatedAt(TimeUtil.toEpochMillis(c.getCreatedAt()));
        vo.setUpdatedAt(TimeUtil.toEpochMillis(c.getUpdatedAt()));
        return vo;
    }
}
