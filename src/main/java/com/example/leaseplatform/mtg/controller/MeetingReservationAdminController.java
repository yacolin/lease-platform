package com.example.leaseplatform.mtg.controller;

import com.example.leaseplatform.sys.log.OperationLog;
import com.example.leaseplatform.common.ApiResponse;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.mtg.dto.MeetingReservationVO;
import com.example.leaseplatform.mtg.service.MeetingReservationService;
import com.example.leaseplatform.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 会议室预约管理（商家后台 /api/v1/meeting-reservations/**，仅 user_type=1 管理员 token，
 * 路径已注册到 lease.security.admin-paths）。
 */
@Tag(name = "meetingReservationAdmin", description = "会议室预约管理（管理端）：预约分页/详情/确认完成")
@RestController
@RequestMapping("/api/v1/meeting-reservations")
@RequiredArgsConstructor
public class MeetingReservationAdminController {

    private final MeetingReservationService reservationService;

    @Operation(operationId = "listMeetingReservations", summary = "预约分页（日期/会议室/状态筛选）")
    @GetMapping
    public ApiResponse<PageResult<MeetingReservationVO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) Integer status) {
        return ApiResponse.ok(reservationService.adminPage(page, size, date, roomId, status));
    }

    @Operation(operationId = "getMeetingReservation", summary = "预约详情")
    @GetMapping("/{id}")
    public ApiResponse<MeetingReservationVO> get(@PathVariable Long id) {
        return ApiResponse.ok(reservationService.adminGet(id));
    }

    @Operation(operationId = "updateMeetingReservationStatus", summary = "状态推进（1.4：已确认→使用中→已完成；2-使用中, 3-已完成）")

    @OperationLog("会议室预约状态推进")
    @PutMapping("/{id}/status")
    public ApiResponse<MeetingReservationVO> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody com.example.leaseplatform.ord.dto.OrderStatusReq req) {
        return ApiResponse.ok(reservationService.adminUpdateStatus(id, req.getOrderStatus(),
                com.example.leaseplatform.security.UserContext.getUserId()));
    }
}
