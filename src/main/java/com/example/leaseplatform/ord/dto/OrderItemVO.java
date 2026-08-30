package com.example.leaseplatform.ord.dto;

import lombok.Data;
import tools.jackson.databind.JsonNode;

/**
 * 订单明细视图对象。
 */
@Data
public class OrderItemVO {

    private Long productId;

    /** 商品名称（快照） */
    private String productName;

    /** 商品原价（分，快照） */
    private Long productPrice;

    /** 规格选择（解析后的对象） */
    private JsonNode specification;

    private Integer quantity;

    /** 小计（分，原价*数量） */
    private Long subtotal;

    /** 折后单价（分） */
    private Long discountedPrice;

    /** 折后小计（分） */
    private Long discountedSubtotal;
}
