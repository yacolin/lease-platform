package com.example.leaseplatform.trd.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 充值记录（trd_recharge_records）：用户充值流水。
 * payment_status：0-待支付, 1-支付成功, 2-支付失败, 3-已退款。
 */
@Data
@TableName("trd_recharge_records")
public class TrdRechargeRecord {

    /** 雪花 ID（资金流水，见 db/README.md 主键 ID 策略） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 充值档位 ID（trd_recharge_tiers.id） */
    private Long tierId;

    /** 充值金额 */
    private BigDecimal rechargeAmount;

    /** 赠送金额 */
    private BigDecimal bonusAmount;

    /** 到账总金额（充值 + 赠送） */
    private BigDecimal totalAmount;

    /** 支付方式：1-微信支付, 2-余额支付 */
    private Integer paymentMethod;

    /** 微信支付交易号 */
    private String transactionId;

    /** 商户订单号（唯一业务号） */
    private String outTradeNo;

    /** 支付状态：0-待支付, 1-支付成功, 2-支付失败, 3-已退款 */
    private Integer paymentStatus;

    /** 支付时间 */
    private LocalDateTime paidAt;

    /** 退款原因 */
    private String refundReason;

    /** 退款时间 */
    private LocalDateTime refundedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
