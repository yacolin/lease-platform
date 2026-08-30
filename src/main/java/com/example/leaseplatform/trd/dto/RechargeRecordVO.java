package com.example.leaseplatform.trd.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 充值记录视图对象。
 */
@Data
public class RechargeRecordVO {

    private Long id;

    /** 商户订单号（唯一业务号，查单/对账用） */
    private String outTradeNo;

    /** 充值档位 ID */
    private Long tierId;

    /** 充值金额（分） */
    private Long rechargeAmount;

    /** 赠送金额（分） */
    private Long bonusAmount;

    /** 到账总金额（分，充值 + 赠送） */
    private Long totalAmount;

    /** 支付方式：1-微信支付, 2-余额支付 */
    @Schema(description = "支付方式：1-微信支付, 2-余额支付")
    private Integer paymentMethod;

    /** 微信支付交易号 */
    private String transactionId;

    /** 支付状态：0-待支付, 1-支付成功, 2-支付失败, 3-已退款 */
    @Schema(description = "支付状态：0-待支付, 1-支付成功, 2-支付失败, 3-已退款")
    private Integer paymentStatus;

    /** 支付时间（epoch 毫秒时间戳） */
    private Long paidAt;

    /** 下单时间（epoch 毫秒时间戳） */
    private Long createdAt;
}
