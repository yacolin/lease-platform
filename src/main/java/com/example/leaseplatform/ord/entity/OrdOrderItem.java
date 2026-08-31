package com.example.leaseplatform.ord.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单明细（ord_order_items）：每个商品的详细信息（含规格快照）。
 * 1.3 起含 SKU 快照（sku_id / sku_name_snapshot / sku_price_snapshot / specification_snapshot），
 * 历史 product_id/product_price/specification 保留（快照原则）。
 * 纯流水表，只保留 created_at。
 */
@Data
@TableName("ord_order_items")
public class OrdOrderItem {

    /** 雪花 ID（订单子表，见 db/README.md 主键 ID 策略） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 订单 ID */
    private Long orderId;

    /** 商品 ID（SPU） */
    private Long productId;

    /** SKU ID（1.3；无 SKU 的历史单为 NULL） */
    private Long skuId;

    /** SKU 名称快照（编码） */
    private String skuNameSnapshot;

    /** SKU 单价快照（分） */
    private Long skuPriceSnapshot;

    /** 规格快照 JSON（SKU 解析后，如 {"杯型":"大杯"}） */
    private String specificationSnapshot;

    /** 商品名称（快照） */
    private String productName;

    /** 商品原价（分，快照） */
    private Long productPrice;

    /** 规格选项 JSON（如大杯/热/无糖） */
    private String specification;

    /** 数量 */
    private Integer quantity;

    /** 小计（分，原价*数量） */
    private Long subtotal;

    /** 折后单价（分） */
    private Long discountedPrice;

    /** 折后小计（分） */
    private Long discountedSubtotal;

    private LocalDateTime createdAt;
}
