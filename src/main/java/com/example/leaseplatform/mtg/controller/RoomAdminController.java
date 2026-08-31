package com.example.leaseplatform.mtg.controller;

import com.example.leaseplatform.common.ApiResponse;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.mtg.dto.RoomReq;
import com.example.leaseplatform.mtg.dto.RoomVO;
import com.example.leaseplatform.mtg.service.RoomService;
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
 * 会议室管理（管理端 /api/v1/rooms/**，仅 user_type=1 管理员 token，
 * 路径已注册到 lease.security.admin-paths）。
 */
@Tag(name = "roomAdmin", description = "会议室管理（管理端）：会议室配置 CRUD")
@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
public class RoomAdminController {

    private final RoomService roomService;

    @Operation(operationId = "createRoom", summary = "创建会议室")
    @PostMapping
    public ApiResponse<RoomVO> create(@Valid @RequestBody RoomReq req) {
        return ApiResponse.ok(roomService.create(req));
    }

    @Operation(operationId = "listRooms", summary = "会议室分页列表")
    @GetMapping
    public ApiResponse<PageResult<RoomVO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(roomService.page(page, size, status, keyword));
    }

    @Operation(operationId = "getRoom", summary = "会议室详情")
    @GetMapping("/{id}")
    public ApiResponse<RoomVO> get(@PathVariable Long id) {
        return ApiResponse.ok(roomService.getById(id));
    }

    @Operation(operationId = "updateRoom", summary = "更新会议室")
    @PutMapping("/{id}")
    public ApiResponse<RoomVO> update(@PathVariable Long id, @Valid @RequestBody RoomReq req) {
        return ApiResponse.ok(roomService.update(id, req));
    }

    @Operation(operationId = "deleteRoom", summary = "删除会议室")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        roomService.delete(id);
        return ApiResponse.ok(null);
    }
}
