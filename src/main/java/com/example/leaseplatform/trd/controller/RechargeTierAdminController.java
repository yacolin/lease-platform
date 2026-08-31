package com.example.leaseplatform.trd.controller;

import com.example.leaseplatform.common.ApiResponse;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.trd.dto.RechargeTierReq;
import com.example.leaseplatform.trd.dto.RechargeTierVO;
import com.example.leaseplatform.trd.service.RechargeTierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 充值档位管理（管理端 /api/v1/recharge-tiers/**，仅 user_type=1 管理员 token，
 * 路径已注册到 lease.security.admin-paths）。
 */
@Tag(name = "rechargeTierAdmin", description = "充值档位管理（管理端）：充值赠送规则配置 CRUD")
@RestController
@RequestMapping("/api/v1/recharge-tiers")
@RequiredArgsConstructor
public class RechargeTierAdminController {

    private final RechargeTierService tierService;

    @Operation(operationId = "createRechargeTier", summary = "创建充值档位")
    @PostMapping
    public ApiResponse<RechargeTierVO> create(@Valid @RequestBody RechargeTierReq req) {
        return ApiResponse.ok(tierService.create(req));
    }

    @Operation(operationId = "listRechargeTiers", summary = "充值档位分页列表")
    @GetMapping
    public ApiResponse<PageResult<RechargeTierVO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer status) {
        return ApiResponse.ok(tierService.page(page, size, status));
    }

    @Operation(operationId = "getRechargeTier", summary = "充值档位详情")
    @GetMapping("/{id}")
    public ApiResponse<RechargeTierVO> get(@PathVariable Long id) {
        return ApiResponse.ok(tierService.getById(id));
    }

    @Operation(operationId = "updateRechargeTier", summary = "更新充值档位")
    @PutMapping("/{id}")
    public ApiResponse<RechargeTierVO> update(@PathVariable Long id,
                                              @Valid @RequestBody RechargeTierReq req) {
        return ApiResponse.ok(tierService.update(id, req));
    }

    @Operation(operationId = "deleteRechargeTier", summary = "删除充值档位")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        tierService.delete(id);
        return ApiResponse.ok(null);
    }
}
