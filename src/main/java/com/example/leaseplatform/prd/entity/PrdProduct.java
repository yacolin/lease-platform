package com.example.leaseplatform.prd.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品（prd_products，1.3 起收拢为 SPU）：product_type 1-咖啡, 2-正餐, 3-加饭/加菜, 4-加汤。
 * price/stock 为 SPU 基准（1.0 遗留），1.3 起售价/库存以 prd_skus 为准；
 * spec_options 以 JSON 字符串原样存取（1.0 遗留），规格结构由 prd_spec_groups/values 承接；
 * product_status：0-草稿, 1-待审核, 2-上架, 3-下架, 4-停售（is_available 保留兼容并联动）。
 */
@Data
@TableName("prd_products")
public class PrdProduct {

    /** 雪花 ID（商品主表，被订单明细关联，见 db/README.md 主键 ID 策略） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 分类ID（prd_categories.id） */
    private Long categoryId;

    /** 商品名称 */
    private String productName;

    /** 商品类型：1-咖啡, 2-正餐, 3-加饭/加菜, 4-加汤 */
    private Integer productType;

    /** 原价（分，SPU 基准价；SKU 售价以 prd_skus.price 为准） */
    private Long price;

    /** 商品描述 */
    private String description;

    /** 商品图片URL */
    private String imageUrl;

    /** 规格选项 JSON 字符串（1.0 遗留，如 {"cup_size":["大杯","中杯"],...}） */
    private String specOptions;

    /** 是否可售：0-下架, 1-上架（与 product_status 联动） */
    private Integer isAvailable;

    /** 商品状态：0-草稿, 1-待审核, 2-上架, 3-下架, 4-停售 */
    private Integer productStatus;

    /** 库存（1.0 遗留；1.3 库存以 prd_skus.stock 为准） */
    private Integer stock;

    /** 排序权重（升序） */
    private Integer sortOrder;

    /** 逻辑删除：0-未删除, 1-已删除 */
    @TableLogic
    private Integer isDeleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
