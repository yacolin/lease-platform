package com.example.leaseplatform.trd.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 充值下单请求（POST /api/v1/me/recharge）。
 */
@Data
public class RechargeReq {

    /** 充值档位 ID（trd_recharge_tiers.id） */
    @NotNull(message = "充值档位不能为空")
    private Long tierId;
}
