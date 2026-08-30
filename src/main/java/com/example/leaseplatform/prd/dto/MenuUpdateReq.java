package com.example.leaseplatform.prd.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 更新每日菜单项请求（PUT 全量更新）。
 */
@Data
public class MenuUpdateReq {

    @NotNull(message = "菜单日期不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate menuDate;

    @NotNull(message = "套餐商品ID不能为空")
    private Long productId;

    @NotBlank(message = "菜品名称不能为空")
    @Size(max = 50, message = "菜品名称最多 50 个字符")
    private String dishName;

    @NotNull(message = "菜品类型不能为空")
    @Min(value = 1, message = "菜品类型只能是 1-荤菜, 2-素菜, 3-汤, 4-饭")
    @Max(value = 4, message = "菜品类型只能是 1-荤菜, 2-素菜, 3-汤, 4-饭")
    @Schema(description = "菜品类型：1-荤菜, 2-素菜, 3-汤, 4-饭")
    private Integer dishType;

    private Integer sortOrder = 0;

    @Schema(description = "是否供应：0-停售, 1-供应")
    private Integer isAvailable = 1;
}
