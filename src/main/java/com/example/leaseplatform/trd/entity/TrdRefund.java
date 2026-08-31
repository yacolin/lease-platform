package com.example.leaseplatform.trd.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 退款单（trd_refunds，1.2 交易可靠性）：支持全额/部分/多次退款，必须关联支付单。
 * refund_method：1-原路余额, 2-原路微信；
 * status：0-处理中, 1-成功, 2-失败；
 * idempotency_key 唯一：业务取消/商家退款各一次，防重复退款（roadmap 1.2「退款幂等」）。
 */
@Data
@TableName("trd_refunds")
public class TrdRefund {

    /** 雪花 ID（资金流水，见 db/README.md 主键 ID 策略） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 退款单编号（唯一） */
    private String refundNo;

    /** 关联支付单 ID（trd_payments.id） */
    private Long paymentId;

    /** 退款用户 ID */
    private Long userId;

    /** 业务类型（同 trd_payments） */
    private Integer bizType;

    /** 业务单 ID */
    private Long bizId;

    /** 退款金额（分） */
    private Long refundAmount;

    /** 退款方式：1-原路余额, 2-原路微信 */
    private Integer refundMethod;

    /** 退款状态：0-处理中, 1-成功, 2-失败 */
    private Integer status;

    /** 幂等键（唯一，防重复退款） */
    private String idempotencyKey;

    /** 退款原因 */
    private String refundReason;

    /** 退款完成时间 */
    private LocalDateTime refundedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
