package com.example.leaseplatform.trd.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 余额流水视图对象。
 */
@Data
public class BalanceTransactionVO {

    private Long id;

    /** 类型：1-充值, 2-消费, 3-退款, 4-赠送, 5-调整 */
    private Integer transactionType;

    /** 变动金额（正数增加，负数减少） */
    private BigDecimal amount;

    /** 变动前余额 */
    private BigDecimal balanceBefore;

    /** 变动后余额 */
    private BigDecimal balanceAfter;

    /** 变动前赠送余额 */
    private BigDecimal giftBalanceBefore;

    /** 变动后赠送余额 */
    private BigDecimal giftBalanceAfter;

    /** 关联订单 ID */
    private Long relatedOrderId;

    /** 关联充值记录 ID */
    private Long relatedRechargeId;

    /** 备注说明 */
    private String remark;

    /** 发生时间（epoch 毫秒时间戳） */
    private Long createdAt;
}
