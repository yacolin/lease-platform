package com.example.leaseplatform.mtg.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 会议室预约（mtg_reservations）。
 * status：0-待确认, 1-已确认, 2-已完成, 3-已取消, 4-已过期；
 * is_free：0-否（超出免费时长）, 1-是；fee_amount：超时计费金额；
 * overtime_unit_price / free_hours_deducted：价格快照（下单时单价与抵扣时长，
 * 规则可改、快照不变，保证历史单对账）。
 */
@Data
@TableName("mtg_reservations")
public class MtgReservation {

    /** 雪花 ID（预约记录，见 db/README.md 主键 ID 策略） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 预约编号（唯一） */
    private String reservationNo;

    /** 会议室 ID */
    private Long roomId;

    /** 预约用户 ID */
    private Long userId;

    /** 企业 ID（个人用户为 0） */
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

    /** 价格快照：下单时超时单价（分/小时） */
    private Long overtimeUnitPrice;

    /** 价格快照：本次抵扣的免费时长（小时） */
    private BigDecimal freeHoursDeducted;

    private LocalDateTime cancelledAt;

    /** 取消原因 */
    private String cancelReason;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
