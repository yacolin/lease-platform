package com.example.leaseplatform.mtg.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 会议室预约视图对象。
 */
@Data
public class MeetingReservationVO {

    private Long id;

    private String reservationNo;

    private Long roomId;

    /** 会议室名称 */
    private String roomName;

    private Long enterpriseId;

    /** 预约日期 */
    private LocalDate reservationDate;

    /** 开始时间 */
    private LocalTime startTime;

    /** 结束时间 */
    private LocalTime endTime;

    /** 时长（小时） */
    private BigDecimal durationHours;

    /** 会议主题 */
    private String meetingTopic;

    /** 状态：0-待确认, 1-已确认, 2-已完成, 3-已取消, 4-已过期 */
    private Integer status;

    /** 是否免费：0-否（超出免费时长）, 1-是 */
    private Integer isFree;

    /** 费用（分） */
    private Long feeAmount;

    /** 取消时间（epoch 毫秒时间戳） */
    private Long cancelledAt;

    private String cancelReason;

    /** 预约时间（epoch 毫秒时间戳） */
    private Long createdAt;
}
