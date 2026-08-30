package com.example.leaseplatform.mtg.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 会议室（mtg_rooms）：会议室基础信息。
 * 自增 ID（配置表，见 db/README.md 主键 ID 策略）；
 * status：0-维护中, 1-可预约；hourly_fee：超出费用（元/小时）。
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

    /** 超出费用（元/小时） */
    private BigDecimal hourlyFee;

    /** 状态：0-维护中, 1-可预约 */
    private Integer status;

    /** 逻辑删除：0-未删除, 1-已删除 */
    @TableLogic
    private Integer isDeleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
