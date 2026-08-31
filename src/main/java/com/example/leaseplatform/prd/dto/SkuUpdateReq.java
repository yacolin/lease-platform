package com.example.leaseplatform.prd.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.List;

/**
 * 更新 SKU 请求（1.3）。
 */
@Data
public class SkuUpdateReq {

    /** SKU 编码 */
    private String skuCode;

    /** 规格值 ID 组合（prd_spec_values.id 列表，可空） */
    private List<Long> specValueIds;

    @Min(value = 0, message = "售价不能为负数")
    private Long price;

    @Min(value = 0, message = "成本价不能为负数")
    private Long costPrice;

    private Integer stock;

    @Schema(description = "状态：0-停售, 1-可售")
    private Integer status;

    private Integer sortOrder;
}
