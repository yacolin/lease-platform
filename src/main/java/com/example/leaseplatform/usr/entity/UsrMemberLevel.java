package com.example.leaseplatform.usr.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 会员等级配置（usr_member_levels）：基础版 / VIP / SVIP。
 * 自增 ID（配置表，见 db/README.md 主键 ID 策略）。
 */
@Data
@TableName("usr_member_levels")
public class UsrMemberLevel {

    /** 自增 ID（配置表） */
    @TableId(type = IdType.AUTO)
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

    /** 状态：0-禁用, 1-启用 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
