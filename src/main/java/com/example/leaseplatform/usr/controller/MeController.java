package com.example.leaseplatform.usr.controller;

import com.example.leaseplatform.common.ApiResponse;
import com.example.leaseplatform.usr.dto.MeUpdateReq;
import com.example.leaseplatform.usr.dto.MeVO;
import com.example.leaseplatform.usr.service.UsrUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 我的接口（/api/v1/me，需登录）：
 * 个人资料 + 余额 + 会员等级。
 */
@Tag(name = "me", description = "我的：当前登录用户（需登录，JWT Bearer）")
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MeController {

    private final UsrUserService userService;

    @Operation(summary = "获取我的资料")
    @GetMapping
    public ApiResponse<MeVO> me() {
        return ApiResponse.ok(userService.me());
    }

    @Operation(summary = "更新我的资料（昵称 / 头像 / 手机号）")
    @PutMapping
    public ApiResponse<MeVO> updateMe(@Valid @RequestBody MeUpdateReq req) {
        return ApiResponse.ok(userService.updateMe(req));
    }
}
