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

    private Integer sortOrder;

    @Schema(description = "状态：0-禁用, 1-启用")
    private Integer status;

    /** 创建时间（epoch 毫秒时间戳） */
    private Long createdAt;

    /** 更新时间（epoch 毫秒时间戳） */
    private Long updatedAt;
}
