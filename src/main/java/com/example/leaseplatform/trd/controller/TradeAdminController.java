package com.example.leaseplatform.trd.controller;

import com.example.leaseplatform.common.ApiResponse;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.ord.dto.OrderStatusHistoryVO;
import com.example.leaseplatform.ord.service.OrderStatusHistoryService;
import com.example.leaseplatform.trd.dto.PaymentVO;
import com.example.leaseplatform.trd.dto.RefundVO;
import com.example.leaseplatform.trd.service.PaymentService;
import com.example.leaseplatform.trd.service.RefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 交易管理（1.2，管理端 /api/v1/payments、/api/v1/refunds、/api/v1/status-history，
 * 仅 user_type=1 管理员 token，路径已注册到 lease.security.admin-paths）。
 * 资金链路可追踪：支付单 → 退款单 → 订单状态历史（roadmap 1.2 完成标准）。
 */
@Tag(name = "tradeAdmin", description = "交易管理（管理端，1.2）：支付单/退款单/订单状态历史查询")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TradeAdminController {

    private final PaymentService paymentService;
    private final RefundService refundService;
    private final OrderStatusHistoryService statusHistoryService;

    @Operation(operationId = "listPayments", summary = "支付单分页（编号/业务类型/状态筛选）")
    @GetMapping("/payments")
    public ApiResponse<PageResult<PaymentVO>> payments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String paymentNo,
            @RequestParam(required = false) Integer bizType,
            @RequestParam(required = false) Integer status) {
        return ApiResponse.ok(paymentService.adminPage(page, size, paymentNo, bizType, status));
    }

    @Operation(operationId = "listRefunds", summary = "退款单分页（编号/状态筛选）")
    @GetMapping("/refunds")
    public ApiResponse<PageResult<RefundVO>> refunds(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String refundNo,
            @RequestParam(required = false) Integer status) {
        return ApiResponse.ok(refundService.adminPage(page, size, refundNo, status));
    }

    @Operation(operationId = "getOrderStatusHistory", summary = "订单状态历史（bizType：1-咖啡订单, 2-正餐预订）")
    @GetMapping("/status-history")
    public ApiResponse<List<OrderStatusHistoryVO>> statusHistory(
            @RequestParam Integer bizType,
            @RequestParam Long bizId) {
        return ApiResponse.ok(statusHistoryService.admin(bizType, bizId));
    }
}
