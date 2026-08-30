package com.example.leaseplatform.mtg.controller;

import com.example.leaseplatform.common.ApiResponse;
import com.example.leaseplatform.mtg.dto.RoomVO;
import com.example.leaseplatform.mtg.service.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 会议室公开列表（/api/v1/public/rooms，白名单，无需登录）。
 */
@Tag(name = "会议室浏览（公开）", description = "无需认证，面向小程序端")
@RestController
@RequestMapping("/api/v1/public/rooms")
@RequiredArgsConstructor
public class RoomPublicController {

    private final RoomService roomService;

    @Operation(summary = "可预约会议室列表")
    @GetMapping
    public ApiResponse<List<RoomVO>> list() {
        return ApiResponse.ok(roomService.publicList());
    }
}
