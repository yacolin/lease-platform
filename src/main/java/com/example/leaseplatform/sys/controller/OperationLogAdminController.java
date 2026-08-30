package com.example.leaseplatform.sys.controller;

import com.example.leaseplatform.common.ApiResponse;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.sys.dto.OperationLogVO;
import com.example.leaseplatform.sys.service.OperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志查询（管理端 /api/v1/operation-logs/**，仅 user_type=1 管理员 token，
 * 路径已注册到 lease.security.admin-paths）。
 */
@Tag(name = "operationLogAdmin", description = "操作日志（管理端）：后台操作审计：分页/操作人/操作类型筛选")
@RestController
@RequestMapping("/api/v1/operation-logs")
@RequiredArgsConstructor
public class OperationLogAdminController {

    private final OperationLogService operationLogService;

    @Operation(summary = "操作日志分页（操作人/操作类型筛选）")
    @GetMapping
    public ApiResponse<PageResult<OperationLogVO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) String operationType) {
        return ApiResponse.ok(operationLogService.page(page, size, operatorId, operationType));
    }
}
