package com.example.leaseplatform.trd.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 余额流水（trd_balance_transactions）：所有余额变动明细（充值/消费/退款/赠送/调整）。
 * transaction_type：1-充值, 2-消费, 3-退款, 4-赠送, 5-调整。
 * 纯流水表，只保留 created_at。
 */
@Data
@TableName("trd_balance_transactions")
public class TrdBalanceTransaction {

    /** 雪花 ID（资金流水，见 db/README.md 主键 ID 策略） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户 ID */
    private Long userId;

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

    /** 关联订单 ID（订单表） */
    private Long relatedOrderId;

    /** 关联充值记录 ID */
    private Long relatedRechargeId;

    /** 备注说明 */
    private String remark;

    private LocalDateTime createdAt;
}
