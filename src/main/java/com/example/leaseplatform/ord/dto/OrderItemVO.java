package com.example.leaseplatform.ord.dto;

import lombok.Data;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;

/**
 * 订单明细视图对象。
 */
@Data
public class OrderItemVO {

    private Long productId;

    /** 商品名称（快照） */
    private String productName;

    /** 商品原价（快照） */
    private BigDecimal productPrice;

    /** 规格选择（解析后的对象） */
    private JsonNode specification;

    private Integer quantity;

    /** 小计（原价*数量） */
    private BigDecimal subtotal;

    /** 折后单价 */
    private BigDecimal discountedPrice;

    /** 折后小计 */
    private BigDecimal discountedSubtotal;
}
