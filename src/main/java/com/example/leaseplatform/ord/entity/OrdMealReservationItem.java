package com.example.leaseplatform.ord.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 正餐预订明细（ord_meal_reservation_items）：套餐快照 + 当天菜单菜品明细快照。
 * 纯流水表，只保留 created_at。
 */
@Data
@TableName("ord_meal_reservation_items")
public class OrdMealReservationItem {

    /** 雪花 ID（子表，见 db/README.md 主键 ID 策略） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 预订 ID */
    private Long reservationId;

    /** 套餐商品 ID */
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

    /** 菜品明细 JSON（当天菜单快照：dishName/dishType/sortOrder） */
    private String dishDetails;

    private LocalDateTime createdAt;
}
