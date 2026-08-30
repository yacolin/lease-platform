package com.example.leaseplatform.mtg.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会议室等级定价（mtg_room_level_prices）：某会议室对某会员等级的超出费用覆盖（Override）。
 * 自增 ID（配置表，见 db/README.md 主键 ID 策略）。
 * 取值逻辑：预约时优先查本表（room_id + level_code + is_active=1），有值用本表价格；
 * 无则回落 usr_member_levels.meeting_overtime_fee（非会员按 BASIC 兜底）。
 */
@Data
@TableName("mtg_room_level_prices")
public class MtgRoomLevelPrice {

    /** 自增 ID（配置表） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会议室 ID */
    private Long roomId;

    /** 会员等级编码：BASIC / VIP / SVIP */
    private String levelCode;

    /** 该会议室针对该等级的超出费用（分/小时） */
    private Long overtimeFee;

    /** 是否启用：0-禁用, 1-启用 */
    private Integer isActive;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
