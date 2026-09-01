package com.example.leaseplatform.mtg.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 会议室预约改期请求（PUT /api/v1/me/meeting-reservations/{id}/reschedule，1.4）：
 * 仅待确认（未支付）或免费已确认可改；付费已确认需先取消重新预约。
 */
@Data
public class MeetingRescheduleReq {

    @NotNull(message = "预约日期不能为空")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate reservationDate;

    @NotNull(message = "开始时间不能为空")
    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime startTime;

    @NotNull(message = "结束时间不能为空")
    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime endTime;

    @NotBlank(message = "会议主题不能为空")
    @Size(max = 100, message = "会议主题最多 100 个字符")
    private String meetingTopic;
}
