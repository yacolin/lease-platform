package com.example.leaseplatform.mtg.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会议室资源占用（mtg_bookings，1.4 会议室资源化）：Room → Resource → Booking 三层模型。
 * 每次预约创建一条占用记录（status=0 占用中），取消/完成/过期释放（status=1）；
 * 时间冲突校验查本表（配合 mtg_rooms 行锁 SELECT ... FOR UPDATE 串行化并发预约）。
 */
@Data
@TableName("mtg_bookings")
public class MtgBooking {

    /** 占用中 */
    public static final int STATUS_OCCUPIED = 0;
    /** 已释放 */
    public static final int STATUS_RELEASED = 1;

    /** 雪花 ID（业务主表，见 db/README.md 主键 ID 策略） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 会议室 ID */
    private Long roomId;

    /** 预约 ID（mtg_reservations.id） */
    private Long reservationId;

    /** 占用开始时间 */
    private LocalDateTime startAt;

    /** 占用结束时间 */
    private LocalDateTime endAt;

    /** 状态：0-占用中, 1-已释放 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
