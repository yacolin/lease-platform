package com.example.leaseplatform.mtg.controller;

import com.example.leaseplatform.common.ApiResponse;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.mtg.dto.RoomLevelPriceReq;
import com.example.leaseplatform.mtg.dto.RoomLevelPriceVO;
import com.example.leaseplatform.mtg.service.RoomLevelPriceService;
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

import java.util.List;

/**
 * 会议室等级定价管理（管理端 /api/v1/room-level-prices/**，仅 user_type=1 管理员 token，
 * 路径已注册到 lease.security.admin-paths）。
 */
@Tag(name = "roomLevelPriceAdmin", description = "会议室等级定价管理（管理端）：会议室 × 会员等级 超出费用覆盖配置 CRUD")
@RestController
@RequestMapping("/api/v1/room-level-prices")
@RequiredArgsConstructor
public class RoomLevelPriceAdminController {

    private final RoomLevelPriceService priceService;

    @Operation(summary = "创建定价（同会议室同等级重复 → 409）")
    @PostMapping
    public ApiResponse<RoomLevelPriceVO> create(@Valid @RequestBody RoomLevelPriceReq req) {
        return ApiResponse.ok(priceService.create(req));
    }

    @Operation(summary = "定价分页列表（可按会议室过滤）")
    @GetMapping
    public ApiResponse<PageResult<RoomLevelPriceVO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long roomId) {
        return ApiResponse.ok(priceService.page(page, size, roomId));
    }

    @Operation(summary = "某会议室全部定价")
    @GetMapping("/rooms/{roomId}")
    public ApiResponse<List<RoomLevelPriceVO>> listByRoom(@PathVariable Long roomId) {
        return ApiResponse.ok(priceService.listByRoom(roomId));
    }

    @Operation(summary = "定价详情")
    @GetMapping("/{id}")
    public ApiResponse<RoomLevelPriceVO> get(@PathVariable Long id) {
        return ApiResponse.ok(priceService.getById(id));
    }

    @Operation(summary = "更新定价")
    @PutMapping("/{id}")
    public ApiResponse<RoomLevelPriceVO> update(@PathVariable Long id, @Valid @RequestBody RoomLevelPriceReq req) {
        return ApiResponse.ok(priceService.update(id, req));
    }

    @Operation(summary = "删除定价")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        priceService.delete(id);
        return ApiResponse.ok(null);
    }
}
