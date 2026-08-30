package com.example.leaseplatform.trd.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 充值档位视图对象。
 */
@Data
public class RechargeTierVO {

    private Long id;

    /** 充值金额 */
    private BigDecimal rechargeAmount;

    /** 赠送金额 */
    private BigDecimal bonusAmount;

    /** 实际到账金额（充值 + 赠送） */
    private BigDecimal actualAmount;

    /** 相当于折扣（如 0.91） */
    private BigDecimal equivalentDiscount;

    private Integer sortOrder;

    /** 状态：0-禁用, 1-启用 */
    private Integer status;

    /** 创建时间（epoch 毫秒时间戳） */
    private Long createdAt;
}
