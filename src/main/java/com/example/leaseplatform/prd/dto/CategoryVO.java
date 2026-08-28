package com.example.leaseplatform.prd.dto;

import lombok.Data;


/**
 * 商品分类视图对象。
 */
@Data
public class CategoryVO {

    private Long id;

    private String categoryName;

    private Integer categoryType;

    private Integer sortOrder;

    private Integer status;

    /** 创建时间（epoch 毫秒时间戳） */
    private Long createdAt;

    /** 更新时间（epoch 毫秒时间戳） */
    private Long updatedAt;
}
