package com.example.leaseplatform.mkt.controller;

import com.example.leaseplatform.common.ApiResponse;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.mkt.dto.CouponCreateReq;
import com.example.leaseplatform.mkt.dto.CouponUpdateReq;
import com.example.leaseplatform.mkt.dto.CouponVO;
import com.example.leaseplatform.mkt.service.MktCouponService;
import com.example.leaseplatform.sys.log.OperationLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 优惠券管理（1.6，管理端 /api/v1/coupons/**，仅 user_type=1 管理员 token，
 * 路径已注册到 lease.security.admin-paths）。
 */
@Tag(name = "couponAdmin", description = "优惠券管理（管理端，1.6）：券模板 CRUD / 停用")
@RestController
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
public class MktCouponAdminController {

    private final MktCouponService couponService;

    @Operation(operationId = "createCoupon", summary = "创建优惠券模板（满减/折扣，可指定业务/商品/分类）")
    @OperationLog("创建优惠券")
    @PostMapping
    public ApiResponse<CouponVO> create(@Valid @RequestBody CouponCreateReq req) {
        return ApiResponse.ok(couponService.create(req));
    }

    @Operation(operationId = "listCoupons", summary = "优惠券分页（类型/状态/名称筛选）")
    @GetMapping
    public ApiResponse<PageResult<CouponVO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer couponType,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(couponService.page(page, size, couponType, status, keyword));
    }

    @Operation(operationId = "getCoupon", summary = "优惠券详情")
    @GetMapping("/{id}")
    public ApiResponse<CouponVO> get(@PathVariable Long id) {
        return ApiResponse.ok(couponService.getById(id));
    }

    @Operation(operationId = "updateCoupon", summary = "更新优惠券模板（已领取用户券保留领取时快照）")
    @PutMapping("/{id}")
    public ApiResponse<CouponVO> update(@PathVariable Long id,
                                        @Valid @RequestBody CouponUpdateReq req) {
        return ApiResponse.ok(couponService.update(id, req));
    }

    @Operation(operationId = "deleteCoupon", summary = "删除优惠券（已被用户领取时拒绝，可改为停用）")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        couponService.delete(id);
        return ApiResponse.ok(null);
    }
}
