package com.example.leaseplatform.ord.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 咖啡下单请求（POST /api/v1/me/orders）。
 */
@Data
public class OrderCreateReq {

    @NotEmpty(message = "至少需要一个商品")
    @Size(max = 20, message = "单笔订单最多 20 个商品项")
    @Valid
    private List<OrderItemReq> items;

    /** 支付方式：1-余额支付（P3 支持），2-微信支付（预留） */
    @Min(value = 1, message = "支付方式：1-余额, 2-微信")
    @Max(value = 2, message = "支付方式：1-余额, 2-微信")
    @Schema(description = "支付方式：1-余额支付, 2-微信支付")
    private Integer paymentMethod = 1;

    @Size(max = 255, message = "备注最多 255 个字符")
    private String remark;
}
