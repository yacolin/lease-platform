package com.example.leaseplatform.prd.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建商品分类请求。
 */
@Data
public class CategoryCreateReq {

    @NotBlank(message = "分类名称不能为空")
    @Size(max = 50, message = "分类名称最多 50 个字符")
    private String categoryName;

    @NotNull(message = "分类类型不能为空")
    @Min(value = 1, message = "分类类型只能是 1-咖啡 或 2-正餐")
    @Max(value = 2, message = "分类类型只能是 1-咖啡 或 2-正餐")
    @Schema(description = "分类类型：1-咖啡, 2-正餐")
    private Integer categoryType;

    /** 父分类 ID（0=一级分类） */
    private Long parentId = 0L;

    private Integer sortOrder = 0;

    @Schema(description = "状态：0-禁用, 1-启用")
    private Integer status = 1;

    /** 分类图标 URL */
    private String iconUrl;

    /** 是否展示：0-不展示, 1-展示 */
    private Integer isShow = 1;
}
