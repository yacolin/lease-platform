package com.example.leaseplatform.trd.controller;

import com.example.leaseplatform.common.ApiResponse;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.ord.dto.OrderStatusHistoryVO;
import com.example.leaseplatform.ord.service.OrderStatusHistoryService;
import com.example.leaseplatform.security.UserContext;
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
 * 我的交易（1.2，小程序端 /api/v1/me/**，需登录）：
 * 支付单 / 退款单 / 订单状态历史查询（roadmap 1.2「交易可信」）。
 */
@Tag(name = "myTrade", description = "我的交易（1.2）：支付单/退款单/订单状态历史（需登录）")
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MeTradeController {

    private final PaymentService paymentService;
    private final RefundService refundService;
    private final OrderStatusHistoryService statusHistoryService;

    @Operation(operationId = "listMyPayments", summary = "我的支付单（分页，可选业务类型筛选）")
    @GetMapping("/payments")
    public ApiResponse<PageResult<PaymentVO>> payments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer bizType) {
        return ApiResponse.ok(paymentService.myPayments(UserContext.getUserId(), page, size, bizType));
    }

    @Operation(operationId = "listMyRefunds", summary = "我的退款单（分页）")
    @GetMapping("/refunds")
    public ApiResponse<PageResult<RefundVO>> refunds(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(refundService.myRefunds(UserContext.getUserId(), page, size));
    }

    @Operation(operationId = "getMyOrderStatusHistory", summary = "我的订单状态历史（bizType：1-咖啡订单, 2-正餐预订）")
    @GetMapping("/status-history")
    public ApiResponse<List<OrderStatusHistoryVO>> statusHistory(
            @RequestParam Integer bizType,
            @RequestParam Long bizId) {
        return ApiResponse.ok(statusHistoryService.mine(UserContext.getUserId(), bizType, bizId));
    }
}
