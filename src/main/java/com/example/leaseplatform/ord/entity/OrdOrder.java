package com.example.leaseplatform.ord.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单主表（ord_orders）：咖啡点单 / 正餐预订（P3 先落地咖啡）。
 * order_type：1-咖啡, 2-正餐, 3-加餐；
 * order_status：0-待支付, 1-待取餐, 2-制作中, 3-已完成, 4-已取消, 5-已退款；
 * payment_method：1-余额支付, 2-微信支付。
 */
@Data
@TableName("ord_orders")
public class OrdOrder {

    /** 雪花 ID（订单数据量最大，见 db/README.md 主键 ID 策略） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 订单编号（唯一） */
    private String orderNo;

    /** 下单用户 ID */
    private Long userId;

    /** 企业 ID（若为企业用户） */
    private Long enterpriseId;

    /** 订单类型：1-咖啡, 2-正餐, 3-加餐 */
    private Integer orderType;

    /** 订单状态：0-待支付, 1-待取餐, 2-制作中, 3-已完成, 4-已取消, 5-已退款 */
    private Integer orderStatus;

    /** 商品原价总金额（分） */
    private Long totalAmount;

    /** 折扣优惠金额（合计，分） */
    private Long discountAmount;

    /** 会员等级折扣金额（分） */
    private Long memberDiscount;

    /** 充值赠送折扣金额（分） */
    private Long rechargeDiscount;

    /** 应付金额（分，折后） */
    private Long payableAmount;

    /** 支付方式：1-余额支付, 2-微信支付 */
    private Integer paymentMethod;

    /** 微信支付交易号 */
    private String transactionId;

    /** 商户订单号 */
    private String outTradeNo;

    /** 取餐码（咖啡） */
    private String pickupCode;

    /** 配送方式（正餐）：1-到店自取, 2-楼内配送, 3-周边配送 */
    private Integer deliveryType;

    /** 配送费（分） */
    private Long deliveryFee;

    /** 配送地址（周边配送） */
    private String deliveryAddress;

    /** 预约日期（正餐） */
    private java.time.LocalDate reservationDate;

    /** 预约时段（正餐） */
    private java.time.LocalTime reservationTime;

    /** 订单备注 */
    private String remark;

    /** 支付时间 */
    private LocalDateTime paidAt;

    /** 完成时间 */
    private LocalDateTime completedAt;

    /** 取消时间 */
    private LocalDateTime cancelledAt;

    /** 取消原因 */
    private String cancelReason;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
