package com.example.leaseplatform.prd.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品 SKU（prd_skus，1.3 商品中心 SKU 化）：SKU 承接价格/库存/规格组合。
 * prd_products 收拢为 SPU；sku_code 唯一；spec_value_ids / spec_snapshot 存 JSON。
 * status：0-停售, 1-可售。
 */
@Data
@TableName("prd_skus")
public class PrdSku {

    /** 雪花 ID（业务主表，见 db/README.md 主键 ID 策略；种子显式指定 id） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** SKU 编码（唯一） */
    private String skuCode;

    /** 商品 ID（SPU，prd_products.id） */
    private Long productId;

    /** 规格值 ID 组合 JSON（如 [1,2,3]） */
    private String specValueIds;

    /** 规格快照 JSON（如 {"杯型":"大杯","温度":"热"}） */
    private String specSnapshot;

    /** 售价（分） */
    private Long price;

    /** 成本价（分） */
    private Long costPrice;

    /** 库存（NULL 表示不限） */
    private Integer stock;

    /** 状态：0-停售, 1-可售 */
    private Integer status;

    /** 排序权重（升序） */
    private Integer sortOrder;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
