package com.example.leaseplatform.mtg.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 会议室等级定价创建/更新请求（管理端）。
 * 取值逻辑：预约时优先查本表（room_id + level_code + is_active=1），
 * 无则回落 usr_member_levels.meeting_overtime_fee 默认价。
 */
@Data
public class RoomLevelPriceReq {

    @NotNull(message = "会议室不能为空")
    private Long roomId;

    @NotBlank(message = "会员等级不能为空")
    @Pattern(regexp = "^(BASIC|VIP|SVIP)$", message = "会员等级须为 BASIC/VIP/SVIP")
    @Size(max = 20, message = "会员等级编码过长")
    private String levelCode;

    @NotNull(message = "超出费用不能为空")
    @Min(value = 0, message = "超出费用不能为负")
    private Long overtimeFee;

    @Schema(description = "是否启用：0-禁用, 1-启用")
    private Integer isActive = 1;
}
