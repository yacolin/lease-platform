package com.example.leaseplatform.ord.dto;

import lombok.Data;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;

/**
 * 正餐预订明细视图对象（套餐快照 + 菜品明细快照）。
 */
@Data
public class MealReservationItemVO {

    private Long productId;

    /** 套餐名称（快照） */
    private String productName;

    /** 套餐原价（快照） */
    private BigDecimal productPrice;

    /** 份数 */
    private Integer quantity;

    /** 小计（原价*份数） */
    private BigDecimal subtotal;

    /** 折后单价 */
    private BigDecimal discountedPrice;

    /** 折后小计 */
    private BigDecimal discountedSubtotal;

    /** 菜品明细（当天菜单快照） */
    private JsonNode dishDetails;
}
