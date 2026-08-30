package com.example.leaseplatform.prd.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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

    /** 商品类型：1-咖啡, 2-正餐, 3-加饭/加菜, 4-加汤（注意与菜品类型 dish_type 的枚举不同，勿混淆） */
    @Schema(description = "商品类型：1-咖啡, 2-正餐, 3-加饭/加菜, 4-加汤")
    private Integer productType;

    /** 原价（分） */
    private Long price;

    private String description;

    private String imageUrl;

    /** 规格选项 JSON 对象 */
    private Object specOptions;

    @Schema(description = "上下架状态：0-下架, 1-上架")
    private Integer isAvailable;

    private Integer stock;

    private Integer sortOrder;

    /** 创建时间（epoch 毫秒时间戳） */
    private Long createdAt;

    /** 更新时间（epoch 毫秒时间戳） */
    private Long updatedAt;
}
