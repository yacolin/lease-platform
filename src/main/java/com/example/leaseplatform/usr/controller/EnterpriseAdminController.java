package com.example.leaseplatform.usr.controller;

import com.example.leaseplatform.common.ApiResponse;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.usr.dto.EnterpriseAuditReq;
import com.example.leaseplatform.usr.dto.EnterpriseVO;
import com.example.leaseplatform.usr.service.EnterpriseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 企业审核（管理端 /api/v1/enterprises/**，仅 user_type=1 管理员 token，
 * 路径已注册到 lease.security.admin-paths）。
 */
@Tag(name = "企业审核（管理端）", description = "企业实名注册审核：分页/详情/通过/拒绝")
@RestController
@RequestMapping("/api/v1/enterprises")
@RequiredArgsConstructor
public class EnterpriseAdminController {

    private final EnterpriseService enterpriseService;

    @Operation(summary = "企业分页列表")
    @GetMapping
    public ApiResponse<PageResult<EnterpriseVO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer auditStatus,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(enterpriseService.page(page, size, auditStatus, keyword));
    }

    @Operation(summary = "企业详情")
    @GetMapping("/{id}")
    public ApiResponse<EnterpriseVO> get(@PathVariable Long id) {
        return ApiResponse.ok(enterpriseService.getById(id));
    }

    @Operation(summary = "审核企业（1-通过, 2-拒绝；拒绝必填原因）")
    @PutMapping("/{id}/audit")
    public ApiResponse<EnterpriseVO> audit(@PathVariable Long id,
                                           @Valid @RequestBody EnterpriseAuditReq req) {
        return ApiResponse.ok(enterpriseService.audit(id, req));
    }
}
