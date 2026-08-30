package com.example.leaseplatform.trd.controller;

import com.example.leaseplatform.common.ApiResponse;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.security.UserContext;
import com.example.leaseplatform.trd.dto.BalanceTransactionVO;
import com.example.leaseplatform.trd.dto.RechargeCreateResultVO;
import com.example.leaseplatform.trd.dto.RechargeRecordVO;
import com.example.leaseplatform.trd.dto.RechargeReq;
import com.example.leaseplatform.trd.service.BalanceService;
import com.example.leaseplatform.trd.service.RechargeService;
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
 * 我的充值与余额（小程序端 /api/v1/me/**，需登录）。
 */
@Tag(name = "myRecharge", description = "我的充值/余额：当前登录用户：充值下单/mock 直充/查单/记录/余额流水（需登录）")
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MeRechargeController {

    private final RechargeService rechargeService;
    private final BalanceService balanceService;

    @Operation(summary = "充值下单（微信支付未配置时走 mock-pay）")
    @PostMapping("/recharge")
    public ApiResponse<RechargeCreateResultVO> recharge(@Valid @RequestBody RechargeReq req) {
        return ApiResponse.ok(rechargeService.createRecharge(UserContext.getUserId(), req.getTierId()));
    }

    @Operation(summary = "开发 mock 直充（立即入账；P2 开发模式替代微信支付）")
    @PostMapping("/recharge/{id}/mock-pay")
    public ApiResponse<RechargeRecordVO> mockPay(@PathVariable Long id) {
        return ApiResponse.ok(rechargeService.mockPay(UserContext.getUserId(), id));
    }

    @Operation(summary = "主动查单兜底（已配置微信支付时同步微信侧状态）")
    @PostMapping("/recharge/{id}/query")
    public ApiResponse<RechargeRecordVO> query(@PathVariable Long id) {
        return ApiResponse.ok(rechargeService.query(UserContext.getUserId(), id));
    }

    @Operation(summary = "我的充值记录")
    @GetMapping("/recharge/records")
    public ApiResponse<PageResult<RechargeRecordVO>> records(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(rechargeService.myRecords(UserContext.getUserId(), page, size));
    }

    @Operation(summary = "我的余额流水（充值/消费/退款）")
    @GetMapping("/balance-transactions")
    public ApiResponse<PageResult<BalanceTransactionVO>> transactions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(balanceService.myTransactions(UserContext.getUserId(), page, size));
    }
}
