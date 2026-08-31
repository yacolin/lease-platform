package com.example.leaseplatform.usr.controller;

import com.example.leaseplatform.common.ApiResponse;
import com.example.leaseplatform.usr.dto.MemberLevelVO;
import com.example.leaseplatform.usr.service.MemberPurchaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 会员等级公开浏览（/api/v1/public/member-levels，白名单，无需登录）。
 */
@Tag(name = "memberLevelPublic", description = "会员等级浏览（公开）：无需认证，面向小程序端")
@RestController
@RequestMapping("/api/v1/public/member-levels")
@RequiredArgsConstructor
public class MemberLevelPublicController {

    private final MemberPurchaseService purchaseService;

    @Operation(operationId = "listPublicMemberLevels", summary = "会员等级列表（仅启用）")
    @GetMapping
    public ApiResponse<List<MemberLevelVO>> list() {
        return ApiResponse.ok(purchaseService.publicLevels());
    }
}
