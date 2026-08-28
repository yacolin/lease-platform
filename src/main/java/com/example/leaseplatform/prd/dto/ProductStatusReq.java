package com.example.leaseplatform.prd.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 商品上下架请求（PUT /products/{id}/status）。
 */
@Data
public class ProductStatusReq {

    @NotNull(message = "上下架状态不能为空")
    private Integer isAvailable;
}
