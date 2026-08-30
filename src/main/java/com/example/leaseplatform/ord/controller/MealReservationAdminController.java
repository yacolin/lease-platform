package com.example.leaseplatform.ord.controller;

import com.example.leaseplatform.common.ApiResponse;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.ord.dto.MealReservationVO;
import com.example.leaseplatform.ord.dto.OrderStatusReq;
import com.example.leaseplatform.ord.service.MealReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 正餐预订管理（商家后台 /api/v1/meal-reservations/**，仅 user_type=1 管理员 token，
 * 路径已注册到 lease.security.admin-paths）。
 */
@Tag(name = "正餐预订管理（管理端）", description = "备餐状态流转：分页/详情/1→2→3、1/2→5 退款")
@RestController
@RequestMapping("/api/v1/meal-reservations")
@RequiredArgsConstructor
public class MealReservationAdminController {

    private final MealReservationService reservationService;

    @Operation(summary = "预订分页（日期/状态筛选）")
    @GetMapping
    public ApiResponse<PageResult<MealReservationVO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Integer status) {
        return ApiResponse.ok(reservationService.adminPage(page, size, date, status));
    }

    @Operation(summary = "预订详情")
    @GetMapping("/{id}")
    public ApiResponse<MealReservationVO> get(@PathVariable Long id) {
        return ApiResponse.ok(reservationService.adminGet(id));
    }

    @Operation(summary = "备餐状态推进（1→2→3；1/2→5 退款）")
    @PutMapping("/{id}/status")
    public ApiResponse<MealReservationVO> updateStatus(@PathVariable Long id,
                                                       @Valid @RequestBody OrderStatusReq req) {
        return ApiResponse.ok(reservationService.adminUpdateStatus(id, req.getOrderStatus()));
    }
}
