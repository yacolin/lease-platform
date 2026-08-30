package com.example.leaseplatform.ord.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * 下单明细项：商品 + 数量 + 规格选择（如 {"cup_size":"大杯","temperature":"热"}）。
 */
@Data
public class OrderItemReq {

    @NotNull(message = "商品不能为空")
    private Long productId;

    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量至少 1")
    @Max(value = 99, message = "单商品最多 99 份")
    private Integer quantity;

    /** 规格选择（商品 spec_options 各维度的取值），快照入库 */
    private Map<String, Object> spec;
}
