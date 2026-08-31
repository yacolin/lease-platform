package com.example.leaseplatform.prd.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 创建 SKU 请求（1.3）。specValueIds 为空表示默认 SKU（无规格）。
 */
@Data
public class SkuCreateReq {

    /** SKU 编码（可选，缺省自动生成） */
    private String skuCode;

    /** 规格值 ID 组合（prd_spec_values.id 列表，可空） */
    private List<Long> specValueIds;

    @NotNull(message = "售价不能为空")
    @Min(value = 0, message = "售价不能为负数")
    private Long price;

    @Min(value = 0, message = "成本价不能为负数")
    private Long costPrice;

    private Integer stock;

    @Schema(description = "状态：0-停售, 1-可售")
    private Integer status = 1;

    private Integer sortOrder = 0;
}
