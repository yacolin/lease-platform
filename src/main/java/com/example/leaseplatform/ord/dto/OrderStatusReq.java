package com.example.leaseplatform.ord.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 商家订单状态推进请求（1-待取餐→2-制作中→3-已完成；1/2→5-已退款）。
 */
@Data
public class OrderStatusReq {

    @NotNull(message = "目标状态不能为空")
    @Min(value = 1, message = "状态范围 1-5")
    @Max(value = 5, message = "状态范围 1-5")
    private Integer orderStatus;
}
