package com.example.leaseplatform.mtg.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 本月剩余免费会议室时长视图对象。
 */
@Data
public class MeetingFreeHoursVO {

    /** 会员每月免费时长（小时） */
    private BigDecimal totalHours;

    /** 本月已用免费时长（小时） */
    private BigDecimal usedHours;

    /** 本月剩余免费时长（小时） */
    private BigDecimal remainingHours;
}
