package com.example.leaseplatform.prd.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * SKU 视图对象（1.3）。
 */
@Data
public class SkuVO {

    private Long id;

    /** SKU 编码 */
    private String skuCode;

    /** 商品 ID（SPU） */
    private Long productId;

    /** 规格值 ID 组合（如 [1,2,3]） */
    private Object specValueIds;

    /** 规格快照（如 {"杯型":"大杯","温度":"热"}） */
    private Object specSnapshot;

    /** 售价（分） */
    private Long price;

    /** 成本价（分） */
    private Long costPrice;

    /** 库存（NULL 表示不限） */
    private Integer stock;

    @Schema(description = "状态：0-停售, 1-可售")
    private Integer status;

    private Integer sortOrder;

    /** 创建时间（epoch 毫秒） */
    private Long createdAt;

    /** 更新时间（epoch 毫秒） */
    private Long updatedAt;
}
