package com.example.leaseplatform.mtg.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 会议室预约请求（POST /api/v1/me/meeting-reservations）。
 */
@Data
public class MeetingReservationCreateReq {

    @NotNull(message = "会议室不能为空")
    private Long roomId;

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
