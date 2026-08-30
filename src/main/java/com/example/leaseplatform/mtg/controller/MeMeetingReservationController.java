package com.example.leaseplatform.mtg.controller;

import com.example.leaseplatform.common.ApiResponse;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.mtg.dto.MeetingFreeHoursVO;
import com.example.leaseplatform.mtg.dto.MeetingReservationCreateReq;
import com.example.leaseplatform.mtg.dto.MeetingReservationVO;
import com.example.leaseplatform.mtg.service.MeetingReservationService;
import com.example.leaseplatform.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 我的会议室预约（小程序端 /api/v1/me/meeting-reservations/**，需登录）。
 */
@Tag(name = "myMeetingReservations", description = "我的会议室预约：当前登录用户：预约（冲突校验/免费时长/超时计费）/支付/取消/查询（需登录）")
@RestController
@RequestMapping("/api/v1/me/meeting-reservations")
@RequiredArgsConstructor
public class MeMeetingReservationController {

    private final MeetingReservationService reservationService;

    @Operation(summary = "预约会议室（时段冲突 409；会员免费时长抵扣，超出按等级/会议室定价计费并写入价格快照）")
    @PostMapping
    public ApiResponse<MeetingReservationVO> create(@Valid @RequestBody MeetingReservationCreateReq req) {
        return ApiResponse.ok(reservationService.create(UserContext.getUserId(), req));
    }

    @Operation(summary = "余额支付付费预约（待确认 → 已确认）")
    @PostMapping("/{id}/pay")
    public ApiResponse<MeetingReservationVO> pay(@PathVariable Long id) {
        return ApiResponse.ok(reservationService.pay(UserContext.getUserId(), id));
    }

    @Operation(summary = "取消预约（已付费原路退款）")
    @PostMapping("/{id}/cancel")
    public ApiResponse<MeetingReservationVO> cancel(@PathVariable Long id,
                                                    @RequestBody(required = false)
                                                    com.example.leaseplatform.ord.dto.CancelReq req) {
        return ApiResponse.ok(reservationService.cancel(UserContext.getUserId(), id,
                req == null ? null : req.getReason()));
    }

    @Operation(summary = "我的预约分页")
    @GetMapping
    public ApiResponse<PageResult<MeetingReservationVO>> myReservations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer status) {
        return ApiResponse.ok(reservationService.myReservations(UserContext.getUserId(), page, size, status));
    }

    @Operation(summary = "预约详情")
    @GetMapping("/{id}")
    public ApiResponse<MeetingReservationVO> get(@PathVariable Long id) {
        return ApiResponse.ok(reservationService.getMine(UserContext.getUserId(), id));
    }

    @Operation(summary = "指定月份剩余免费会议室时长（缺省当月）")
    @GetMapping("/free-hours")
    public ApiResponse<MeetingFreeHoursVO> freeHours(
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM")
            java.time.YearMonth month) {
        return ApiResponse.ok(reservationService.freeHours(UserContext.getUserId(), month));
    }
}
