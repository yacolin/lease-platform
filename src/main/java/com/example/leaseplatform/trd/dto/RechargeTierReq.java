package com.example.leaseplatform.trd.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 充值档位创建/更新请求（管理端）。
 * actual_amount 由服务端按 recharge + bonus 计算，无需传。
 */
@Data
public class RechargeTierReq {

    @NotNull(message = "充值金额不能为空")
    @Min(value = 1, message = "充值金额必须大于 0")
    private Long rechargeAmount;

    @NotNull(message = "赠送金额不能为空")
    @Min(value = 0, message = "赠送金额不能为负")
    private Long bonusAmount;

    @NotNull(message = "相当于折扣不能为空")
    @DecimalMin(value = "0.01", message = "折扣率范围 0.01~9.99")
    @DecimalMax(value = "9.99", message = "折扣率范围 0.01~9.99")
    private BigDecimal equivalentDiscount;

    private Integer sortOrder = 0;

    private Integer status = 1;
}
