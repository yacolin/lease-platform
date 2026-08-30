package com.example.leaseplatform.mtg.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 会议室等级定价视图对象。
 */
@Data
public class RoomLevelPriceVO {

    private Long id;

    /** 会议室 ID */
    private Long roomId;

    /** 会议室名称 */
    private String roomName;

    /** 会员等级编码：BASIC / VIP / SVIP */
    private String levelCode;

    /** 该会议室针对该等级的超出费用（分/小时） */
    private Long overtimeFee;

    /** 是否启用：0-禁用, 1-启用 */
    @Schema(description = "是否启用：0-禁用, 1-启用")
    private Integer isActive;

    /** 创建时间（epoch 毫秒时间戳） */
    private Long createdAt;
}
