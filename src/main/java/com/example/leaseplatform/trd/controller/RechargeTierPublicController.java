package com.example.leaseplatform.trd.controller;

import com.example.leaseplatform.common.ApiResponse;
import com.example.leaseplatform.trd.dto.RechargeTierVO;
import com.example.leaseplatform.trd.service.RechargeTierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 充值档位公开列表（/api/v1/public/recharge-tiers，白名单，无需登录）。
 */
@Tag(name = "rechargeTierPublic", description = "充值档位浏览（公开）：无需认证，面向小程序端")
@RestController
@RequestMapping("/api/v1/public/recharge-tiers")
@RequiredArgsConstructor
public class RechargeTierPublicController {

    private final RechargeTierService tierService;

    @Operation(summary = "充值档位列表（仅启用）")
    @GetMapping
    public ApiResponse<List<RechargeTierVO>> list() {
        return ApiResponse.ok(tierService.publicList());
    }
}
