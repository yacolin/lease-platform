package com.example.leaseplatform.usr.controller;

import com.example.leaseplatform.common.ApiResponse;
import com.example.leaseplatform.usr.dto.AdminLoginReq;
import com.example.leaseplatform.usr.dto.LogoutReq;
import com.example.leaseplatform.usr.dto.RefreshReq;
import com.example.leaseplatform.usr.dto.TokenVO;
import com.example.leaseplatform.usr.dto.WxLoginReq;
import com.example.leaseplatform.usr.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（/api/v1/auth/**，白名单放行，双登录设计）：
 * - POST /login    后台管理员登录（username + password，user_type=1）
 * - POST /wx-login 微信小程序登录（code，user_type=2/3）
 * - POST /refresh  令牌刷新（轮换）
 * - POST /logout   登出（作废 refresh token）
 */
@Tag(name = "auth", description = "认证：后台管理员登录 / 微信小程序登录 / 令牌刷新 / 登出（白名单，无需认证）")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "后台管理员登录（username + password）")
    @PostMapping("/login")
    public ApiResponse<TokenVO> login(@Valid @RequestBody AdminLoginReq req) {
        return ApiResponse.ok(authService.adminLogin(req.getUsername(), req.getPassword()));
    }

    @Operation(summary = "微信小程序登录（code2session）")
    @PostMapping("/wx-login")
    public ApiResponse<TokenVO> wxLogin(@Valid @RequestBody WxLoginReq req) {
        return ApiResponse.ok(authService.wxLogin(req.getCode()));
    }

    @Operation(summary = "刷新令牌（access + refresh 轮换）")
    @PostMapping("/refresh")
    public ApiResponse<TokenVO> refresh(@Valid @RequestBody RefreshReq req) {
        return ApiResponse.ok(authService.refresh(req.getRefreshToken()));
    }

    @Operation(summary = "登出（作废刷新令牌）")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestBody(required = false) LogoutReq req) {
        authService.logout(req == null ? null : req.getRefreshToken());
        return ApiResponse.ok(null);
    }
}
