package com.example.leaseplatform.ord.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 订单视图对象（含明细）。
 */
@Data
public class OrderVO {

    private Long id;

    private String orderNo;

    /** 企业 ID（若为企业用户） */
    private Long enterpriseId;

    /** 订单类型：1-咖啡 */
    @Schema(description = "订单类型：1-咖啡, 2-正餐, 3-加餐")
    private Integer orderType;

    /** 订单状态：0-待支付, 1-待取餐, 2-制作中, 3-已完成, 4-已取消, 5-已退款 */
    @Schema(description = "订单状态：0-待支付, 1-待取餐, 2-制作中, 3-已完成, 4-已取消, 5-已退款")
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
    @Schema(description = "支付方式：1-余额支付, 2-微信支付")
    private Integer paymentMethod;

    /** 取餐码 */
    private String pickupCode;

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

    private List<OrderItemVO> items;
}
