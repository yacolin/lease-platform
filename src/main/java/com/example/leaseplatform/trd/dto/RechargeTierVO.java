package com.example.leaseplatform.trd.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 充值档位视图对象。
 */
@Data
public class RechargeTierVO {

    private Long id;

    /** 充值金额（分） */
    private Long rechargeAmount;

    /** 赠送金额（分） */
    private Long bonusAmount;

    /** 实际到账金额（分，充值 + 赠送） */
    private Long actualAmount;

    /** 相当于折扣（如 0.91） */
    private BigDecimal equivalentDiscount;

    private Integer sortOrder;

    /** 状态：0-禁用, 1-启用 */
    private Integer status;

    /** 创建时间（epoch 毫秒时间戳） */
    private Long createdAt;
}
