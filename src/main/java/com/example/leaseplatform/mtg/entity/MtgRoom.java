package com.example.leaseplatform.mtg.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会议室（mtg_rooms）：会议室基础信息。
 * 自增 ID（配置表，见 db/README.md 主键 ID 策略）；
 * status：0-维护中, 1-可预约。超出费用不再由会议室持有（1.1 定价模型：
 * 见 mtg_room_level_prices 覆盖价 / usr_member_levels.meeting_overtime_fee 默认价）。
 */
@Data
@TableName("mtg_rooms")
public class MtgRoom {

    /** 自增 ID（配置表） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会议室名称 */
    private String roomName;

    /** 容纳人数 */
    private Integer capacity;

    /** 设备（投影仪、白板、音响等） */
    private String equipment;

    /** 适用场景（沙龙、培训、路演、商务洽谈） */
    private String suitableScenes;

    /** 会议室图片 */
    private String imageUrl;

    /** 状态：0-维护中, 1-可预约 */
    private Integer status;

    /** 逻辑删除：0-未删除, 1-已删除 */
    @TableLogic
    private Integer isDeleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
