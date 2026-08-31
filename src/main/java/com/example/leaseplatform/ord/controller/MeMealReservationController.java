package com.example.leaseplatform.ord.controller;

import com.example.leaseplatform.common.ApiResponse;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.ord.dto.MealReservationCreateReq;
import com.example.leaseplatform.ord.dto.MealReservationVO;
import com.example.leaseplatform.ord.service.MealReservationService;
import com.example.leaseplatform.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 我的正餐预订（小程序端 /api/v1/me/meal-reservations/**，需登录）。
 */
@Tag(name = "myMealReservations", description = "我的正餐预订：当前登录用户：按日期+时段预订/余额支付/取消/查询（需登录）")
@RestController
@RequestMapping("/api/v1/me/meal-reservations")
@RequiredArgsConstructor
public class MeMealReservationController {

    private final MealReservationService reservationService;

    @Operation(operationId = "createMealReservation", summary = "正餐预订（提前1天/晚8点截止/可订未来3天）")
    @PostMapping
    public ApiResponse<MealReservationVO> create(@Valid @RequestBody MealReservationCreateReq req) {
        return ApiResponse.ok(reservationService.create(UserContext.getUserId(), req));
    }

    @Operation(operationId = "payMealReservation", summary = "余额支付（赠送余额优先扣）")
    @PostMapping("/{id}/pay")
    public ApiResponse<MealReservationVO> pay(@PathVariable Long id) {
        return ApiResponse.ok(reservationService.pay(UserContext.getUserId(), id));
    }

    @Operation(operationId = "cancelMealReservation", summary = "取消预订（已支付取消原路退款）")
    @PostMapping("/{id}/cancel")
    public ApiResponse<MealReservationVO> cancel(@PathVariable Long id,
                                                 @RequestBody(required = false)
                                                 com.example.leaseplatform.ord.dto.CancelReq req) {
        return ApiResponse.ok(reservationService.cancel(UserContext.getUserId(), id,
                req == null ? null : req.getReason()));
    }

    @Operation(operationId = "listMyMealReservations", summary = "我的预订分页")
    @GetMapping
    public ApiResponse<PageResult<MealReservationVO>> myReservations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer status) {
        return ApiResponse.ok(reservationService.myReservations(UserContext.getUserId(), page, size, status));
    }

    @Operation(operationId = "getMyMealReservation", summary = "预订详情（含菜品快照）")
    @GetMapping("/{id}")
    public ApiResponse<MealReservationVO> get(@PathVariable Long id) {
        return ApiResponse.ok(reservationService.getMine(UserContext.getUserId(), id));
    }
}
