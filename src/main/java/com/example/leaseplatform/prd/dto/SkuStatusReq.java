package com.example.leaseplatform.prd.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * SKU 上下架请求（1.3）。
 */
@Data
public class SkuStatusReq {

    @NotNull(message = "SKU 状态不能为空")
    @Schema(description = "状态：0-停售, 1-可售")
    private Integer status;
}
