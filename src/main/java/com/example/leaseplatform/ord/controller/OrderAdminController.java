package com.example.leaseplatform.ord.controller;

import com.example.leaseplatform.sys.log.OperationLog;
import com.example.leaseplatform.common.ApiResponse;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.ord.dto.OrderStatsVO;
import com.example.leaseplatform.ord.dto.OrderStatusReq;
import com.example.leaseplatform.ord.dto.OrderVO;
import com.example.leaseplatform.ord.dto.VerifyPickupReq;
import com.example.leaseplatform.ord.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 订单管理（商家后台 /api/v1/orders/**，仅 user_type=1 管理员 token，
 * 路径已注册到 lease.security.admin-paths）。
 */
@Tag(name = "订单管理（管理端）", description = "咖啡订单：分页/详情/状态推进/取餐码核销/统计")
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderAdminController {

    private final OrderService orderService;

    @Operation(summary = "订单分页（订单号/状态/日期筛选）")
    @GetMapping
    public ApiResponse<PageResult<OrderVO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(orderService.adminPage(page, size, orderNo, status, date));
    }

    @Operation(summary = "订单详情")
    @GetMapping("/{id}")
    public ApiResponse<OrderVO> get(@PathVariable Long id) {
        return ApiResponse.ok(orderService.adminGet(id));
    }

    @Operation(summary = "状态推进（1→2→3；1/2→5 退款）")

    @OperationLog("订单状态推进")
    @PutMapping("/{id}/status")
    public ApiResponse<OrderVO> updateStatus(@PathVariable Long id,
                                             @Valid @RequestBody OrderStatusReq req) {
        return ApiResponse.ok(orderService.adminUpdateStatus(id, req.getOrderStatus()));
    }

    @Operation(summary = "取餐码核销（待取餐/制作中 → 完成）")
    @PostMapping("/verify-pickup")
    public ApiResponse<OrderVO> verifyPickup(@Valid @RequestBody VerifyPickupReq req) {
        return ApiResponse.ok(orderService.verifyPickup(req.getPickupCode()));
    }

    @Operation(summary = "订单统计（今日订单/金额/待取餐/制作中）")
    @GetMapping("/stats")
    public ApiResponse<OrderStatsVO> stats() {
        return ApiResponse.ok(orderService.stats());
    }
}
