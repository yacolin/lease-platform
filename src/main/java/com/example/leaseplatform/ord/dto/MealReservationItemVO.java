package com.example.leaseplatform.ord.dto;

import lombok.Data;
import tools.jackson.databind.JsonNode;

/**
 * 正餐预订明细视图对象（套餐快照 + 菜品明细快照）。
 */
@Data
public class MealReservationItemVO {

    private Long productId;

    /** 套餐名称（快照） */
    private String productName;

    /** 套餐原价（分，快照） */
    private Long productPrice;

    /** 份数 */
    private Integer quantity;

    /** 小计（分，原价*份数） */
    private Long subtotal;

    /** 折后单价（分） */
    private Long discountedPrice;

    /** 折后小计（分） */
    private Long discountedSubtotal;

    /** 菜品明细（当天菜单快照） */
    private JsonNode dishDetails;
}
