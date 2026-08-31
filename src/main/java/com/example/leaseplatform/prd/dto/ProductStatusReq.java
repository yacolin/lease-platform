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
    @Schema(description = "上下架状态：0-下架, 1-上架（与 product_status 联动）")
    private Integer isAvailable;

    /** 商品状态生命周期：0-草稿, 1-待审核, 2-上架, 3-下架, 4-停售（可选；传了则优先，并同步 is_available） */
    @Schema(description = "商品状态：0-草稿, 1-待审核, 2-上架, 3-下架, 4-停售（可选）")
    private Integer productStatus;
}
