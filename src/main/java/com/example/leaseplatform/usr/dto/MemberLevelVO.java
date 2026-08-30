package com.example.leaseplatform.usr.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 会员等级视图对象（公开列表）。
 */
@Data
public class MemberLevelVO {

    private Long id;

    /** 等级编码：BASIC / VIP / SVIP */
    private String levelCode;

    /** 等级名称 */
    private String levelName;

    /** 价格（分/年） */
    private Long price;

    /** 折扣率（如 0.80 表示 8 折） */
    private BigDecimal discountRate;

    /** 每月免费会议室时长（小时） */
    private Integer monthlyMeetingHours;

    /** 会议室提前预约天数 */
    private Integer meetingBookingAdvanceDays;

    /** 会议室预约优先级：0-无, 1-普通, 2-优先 */
    private Integer meetingPriority;

    /** 会议室超出费用（分/小时） */
    private Long meetingOvertimeFee;

    /** 权益描述 */
    private String description;
}
