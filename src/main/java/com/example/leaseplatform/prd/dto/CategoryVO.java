package com.example.leaseplatform.prd.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;


/**
 * 商品分类视图对象。
 */
@Data
public class CategoryVO {

    private Long id;

    private String categoryName;

    @Schema(description = "分类类型：1-咖啡, 2-正餐")
    private Integer categoryType;

    /** 父分类 ID（0=一级分类） */
    private Long parentId;

    private Integer sortOrder;

    @Schema(description = "状态：0-禁用, 1-启用")
    private Integer status;

    /** 分类图标 URL */
    private String iconUrl;

    /** 是否展示：0-不展示, 1-展示 */
    private Integer isShow;

    /** 创建时间（epoch 毫秒时间戳） */
    private Long createdAt;

    /** 更新时间（epoch 毫秒时间戳） */
    private Long updatedAt;
}
