package com.example.leaseplatform.ord.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 正餐预订（ord_meal_reservations）：按日期+时段预订套餐，关联 ord_orders（order_id）。
 * status：0-待支付, 1-已支付/待备餐, 2-备餐中, 3-已完成, 4-已取消, 5-已退款；
 * delivery_type：1-到店自取, 2-楼内配送, 3-周边配送。
 */
@Data
@TableName("ord_meal_reservations")
public class OrdMealReservation {

    /** 雪花 ID（预订主表，见 db/README.md 主键 ID 策略） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 预订编号（唯一） */
    private String reservationNo;

    /** 预订用户 ID */
    private Long userId;

    /** 企业 ID（若为企业用户） */
    private Long enterpriseId;

    /** 关联订单 ID（ord_orders，order_type=2） */
    private Long orderId;

    /** 套餐商品 ID */
    private Long productId;

    /** 套餐名称（快照） */
    private String productName;

    /** 菜单日期（预订的日期） */
    private LocalDate menuDate;

    /** 预订日期 */
    private LocalDate reservationDate;

    /** 预订时段：午餐/晚餐 */
    private String reservationTimeSlot;

    /** 份数 */
    private Integer quantity;

    /** 配送方式：1-到店自取, 2-楼内配送, 3-周边配送 */
    private Integer deliveryType;

    /** 配送费（分） */
    private Long deliveryFee;

    /** 配送地址（周边配送） */
    private String deliveryAddress;

    /** 总金额（分，套餐原价合计） */
    private Long totalAmount;

    /** 折扣金额（分） */
    private Long discountAmount;

    /** 应付金额（分） */
    private Long payableAmount;

    /** 支付方式：1-余额支付, 2-微信支付 */
    private Integer paymentMethod;

    /** 微信支付交易号 */
    private String transactionId;

    /** 商户订单号 */
    private String outTradeNo;

    /** 状态：0-待支付, 1-已支付/待备餐, 2-备餐中, 3-已完成, 4-已取消, 5-已退款 */
    private Integer status;

    /** 备注 */
    private String remark;

    private LocalDateTime paidAt;

    private LocalDateTime completedAt;

    private LocalDateTime cancelledAt;

    /** 取消原因 */
    private String cancelReason;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
