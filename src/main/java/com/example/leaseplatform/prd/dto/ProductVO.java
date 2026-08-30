package com.example.leaseplatform.prd.dto;

import lombok.Data;

/**
 * 商品视图对象（含分类名称；specOptions 解析为 JSON 对象返回）。
 */
@Data
public class ProductVO {

    private Long id;

    private Long categoryId;

    /** 分类名称（冗余展示） */
    private String categoryName;

    private String productName;

    private Integer productType;

    /** 原价（分） */
    private Long price;

    private String description;

    private String imageUrl;

    /** 规格选项 JSON 对象 */
    private Object specOptions;

    private Integer isAvailable;

    private Integer stock;

    private Integer sortOrder;

    /** 创建时间（epoch 毫秒时间戳） */
    private Long createdAt;

    /** 更新时间（epoch 毫秒时间戳） */
    private Long updatedAt;
}
