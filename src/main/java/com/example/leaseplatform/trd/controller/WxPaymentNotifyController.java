package com.example.leaseplatform.trd.controller;

import com.example.leaseplatform.common.ApiResponse;
import com.example.leaseplatform.trd.service.RechargeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 微信支付结果回调（POST /api/v1/wx/payments/notify，白名单，微信服务器调用，无需业务鉴权头）。
 * 微信支付未配置（开发环境）时返回失败，前端走 mock-pay 直充。
 */
@Tag(name = "微信支付回调", description = "微信服务器调用（白名单）；开发环境未配置微信支付时不可用")
@RestController
@RequestMapping("/api/v1/wx/payments")
@RequiredArgsConstructor
public class WxPaymentNotifyController {

    private final RechargeService rechargeService;

    @Operation(summary = "支付结果回调 notify（微信 V3）", hidden = true)
    @PostMapping("/notify")
    public ResponseEntity<Map<String, String>> notify(@RequestBody String body) {
        try {
            rechargeService.handleNotify(body);
            return ResponseEntity.ok(Map.of("code", "SUCCESS", "message", "成功"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("code", "FAIL", "message", e.getMessage()));
        }
    }
}
