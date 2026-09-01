package com.example.leaseplatform.trd.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 余额流水视图对象。
 */
@Data
public class BalanceTransactionVO {

    private Long id;

    /** 类型：1-充值, 2-消费, 3-退款, 4-赠送, 5-调整 */
    @Schema(description = "类型：1-充值, 2-消费, 3-退款, 4-赠送, 5-调整, 6-冻结, 7-解冻")
    private Integer transactionType;

    /** 变动金额（分，正数增加，负数减少） */
    private Long amount;

    /** 变动前余额（分） */
    private Long balanceBefore;

    /** 变动后余额（分） */
    private Long balanceAfter;

    /** 变动前赠送余额（分） */
    private Long giftBalanceBefore;

    /** 变动后赠送余额（分） */
    private Long giftBalanceAfter;

    /** 变动前冻结余额（分，1.5） */
    private Long frozenBalanceBefore;

    /** 变动后冻结余额（分，1.5） */
    private Long frozenBalanceAfter;

    /** 关联订单 ID */
    private Long relatedOrderId;

    /** 关联充值记录 ID */
    private Long relatedRechargeId;

    /** 备注说明 */
    private String remark;

    /** 发生时间（epoch 毫秒时间戳） */
    private Long createdAt;
}
