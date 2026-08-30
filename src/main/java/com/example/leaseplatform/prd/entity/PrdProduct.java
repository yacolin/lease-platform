package com.example.leaseplatform.prd.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品（prd_products）：product_type 1-咖啡, 2-正餐, 3-加饭/加菜, 4-加汤。
 * spec_options 以 JSON 字符串原样存取，对外返回时解析为对象。
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

    /** 原价（分） */
    private Long price;

    /** 商品描述 */
    private String description;

    /** 商品图片URL */
    private String imageUrl;

    /** 规格选项 JSON 字符串（如 {"cup_size":["大杯","中杯"],"temperature":["热","冰"]}） */
    private String specOptions;

    /** 是否可售：0-下架, 1-上架 */
    private Integer isAvailable;

    /** 库存（NULL 表示不限） */
    private Integer stock;

    /** 排序权重（升序） */
    private Integer sortOrder;

    /** 逻辑删除：0-未删除, 1-已删除 */
    @TableLogic
    private Integer isDeleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
