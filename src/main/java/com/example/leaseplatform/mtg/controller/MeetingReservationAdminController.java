package com.example.leaseplatform.mtg.controller;

import com.example.leaseplatform.common.ApiResponse;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.mtg.dto.MeetingReservationVO;
import com.example.leaseplatform.mtg.service.MeetingReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 会议室预约管理（商家后台 /api/v1/meeting-reservations/**，仅 user_type=1 管理员 token，
 * 路径已注册到 lease.security.admin-paths）。
 */
@Tag(name = "会议室预约管理（管理端）", description = "预约分页/详情/确认完成")
@RestController
@RequestMapping("/api/v1/meeting-reservations")
@RequiredArgsConstructor
public class MeetingReservationAdminController {

    private final MeetingReservationService reservationService;

    @Operation(summary = "预约分页（日期/会议室/状态筛选）")
    @GetMapping
    public ApiResponse<PageResult<MeetingReservationVO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) Integer status) {
        return ApiResponse.ok(reservationService.adminPage(page, size, date, roomId, status));
    }

    @Operation(summary = "预约详情")
    @GetMapping("/{id}")
    public ApiResponse<MeetingReservationVO> get(@PathVariable Long id) {
        return ApiResponse.ok(reservationService.adminGet(id));
    }

    @Operation(summary = "确认完成（已确认 → 已完成）")
    @PutMapping("/{id}/complete")
    public ApiResponse<MeetingReservationVO> complete(@PathVariable Long id) {
        return ApiResponse.ok(reservationService.adminComplete(id));
    }
}
