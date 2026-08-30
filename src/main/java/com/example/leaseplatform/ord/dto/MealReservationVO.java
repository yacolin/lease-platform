package com.example.leaseplatform.ord.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * 正餐预订视图对象（含套餐+菜品快照）。
 */
@Data
public class MealReservationVO {

    private Long id;

    private String reservationNo;

    /** 关联订单 ID（ord_orders） */
    private Long orderId;

    private Long productId;

    /** 套餐名称（快照） */
    private String productName;

    /** 菜单日期 */
    private java.time.LocalDate menuDate;

    /** 预订时段：午餐/晚餐 */
    private String reservationTimeSlot;

    /** 份数 */
    private Integer quantity;

    /** 配送方式：1-自取, 2-楼内, 3-周边 */
    @Schema(description = "配送方式：1-到店自取, 2-楼内配送, 3-周边配送")
    private Integer deliveryType;

    /** 配送费（分） */
    private Long deliveryFee;

    private String deliveryAddress;

    /** 总金额（分，套餐原价合计） */
    private Long totalAmount;

    /** 折扣金额（分） */
    private Long discountAmount;

    /** 应付金额（分） */
    private Long payableAmount;

    /** 状态：0-待支付, 1-待备餐, 2-备餐中, 3-已完成, 4-已取消, 5-已退款 */
    @Schema(description = "状态：0-待支付, 1-待备餐, 2-备餐中, 3-已完成, 4-已取消, 5-已退款")
    private Integer status;

    private String remark;

    /** 支付时间（epoch 毫秒时间戳） */
    private Long paidAt;

    /** 完成时间（epoch 毫秒时间戳） */
    private Long completedAt;

    /** 取消时间（epoch 毫秒时间戳） */
    private Long cancelledAt;

    private String cancelReason;

    /** 下单时间（epoch 毫秒时间戳） */
    private Long createdAt;

    /** 明细（套餐 + 当天菜品快照） */
    private List<MealReservationItemVO> items;

    /** 套餐折后单价（分） */
    private Long discountedPrice;
}
