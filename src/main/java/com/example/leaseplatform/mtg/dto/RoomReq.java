package com.example.leaseplatform.mtg.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 会议室创建/更新请求（管理端）。
 */
@Data
public class RoomReq {

    @NotBlank(message = "会议室名称不能为空")
    @Size(max = 50, message = "会议室名称最多 50 个字符")
    private String roomName;

    @NotNull(message = "容纳人数不能为空")
    @Min(value = 1, message = "容纳人数至少 1")
    @Max(value = 1000, message = "容纳人数过大")
    private Integer capacity;

    @Size(max = 255, message = "设备说明过长")
    private String equipment;

    @Size(max = 255, message = "适用场景说明过长")
    private String suitableScenes;

    @Size(max = 255, message = "图片 URL 过长")
    private String imageUrl;

    @NotNull(message = "超出费用不能为空")
    @Min(value = 0, message = "超出费用不能为负")
    private Long hourlyFee;

    @Schema(description = "状态：0-维护中, 1-可预约")
    private Integer status = 1;
}
