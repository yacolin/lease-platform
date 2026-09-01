package com.example.leaseplatform.mkt.controller;

import com.example.leaseplatform.common.ApiResponse;
import com.example.leaseplatform.mkt.dto.CouponVO;
import com.example.leaseplatform.mkt.service.MktCouponService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 优惠券公开浏览（1.6，/api/v1/public/coupons，白名单放行，无需登录）：可领取优惠券列表。
 */
@Tag(name = "couponPublic", description = "优惠券浏览（公开）：可领取优惠券列表（无需认证）")
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class MktCouponPublicController {

    private final MktCouponService couponService;

    @Operation(operationId = "listClaimableCoupons", summary = "可领取优惠券列表（仅启用）")
    @GetMapping("/coupons")
    public ApiResponse<List<CouponVO>> coupons() {
        return ApiResponse.ok(couponService.publicList());
    }
}
