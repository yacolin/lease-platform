package com.example.leaseplatform.prd.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 商品上下架请求（PUT /products/{id}/status）。
 */
@Data
public class ProductStatusReq {

    @NotNull(message = "上下架状态不能为空")
    @Schema(description = "上下架状态：0-下架, 1-上架")
    private Integer isAvailable;
}
