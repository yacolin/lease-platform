package com.example.leaseplatform.mkt.controller;

import com.example.leaseplatform.common.ApiResponse;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.mkt.dto.UserCouponVO;
import com.example.leaseplatform.mkt.service.MktUserCouponService;
import com.example.leaseplatform.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 我的优惠券（1.6，小程序端 /api/v1/me/coupons/**，需登录）：领取 / 我的券列表。
 */
@Tag(name = "myCoupons", description = "我的优惠券（1.6）：领取/我的优惠券（需登录）")
@RestController
@RequestMapping("/api/v1/me/coupons")
@RequiredArgsConstructor
public class MeCouponController {

    private final MktUserCouponService userCouponService;

    @Operation(operationId = "claimCoupon", summary = "领取优惠券（重复领取 409；过期时间 = 领取 + 有效天数）")
    @PostMapping("/{id}/claim")
    public ApiResponse<UserCouponVO> claim(@PathVariable Long id) {
        return ApiResponse.ok(userCouponService.claim(UserContext.getUserId(), id));
    }

    @Operation(operationId = "listMyCoupons", summary = "我的优惠券（分页，状态：0-未使用, 1-已使用, 2-已过期）")
    @GetMapping
    public ApiResponse<PageResult<UserCouponVO>> myCoupons(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer status) {
        return ApiResponse.ok(userCouponService.myCoupons(UserContext.getUserId(), page, size, status));
    }
}
