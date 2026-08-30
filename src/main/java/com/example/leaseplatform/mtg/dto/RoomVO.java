package com.example.leaseplatform.mtg.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 会议室视图对象。
 */
@Data
public class RoomVO {

    private Long id;

    private String roomName;

    /** 容纳人数 */
    private Integer capacity;

    /** 设备 */
    private String equipment;

    /** 适用场景 */
    private String suitableScenes;

    private String imageUrl;

    /** 状态：0-维护中, 1-可预约 */
    @Schema(description = "状态：0-维护中, 1-可预约")
    private Integer status;

    /** 创建时间（epoch 毫秒时间戳） */
    private Long createdAt;
}
