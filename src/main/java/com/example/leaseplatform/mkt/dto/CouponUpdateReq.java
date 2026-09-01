package com.example.leaseplatform.mkt.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 更新优惠券模板请求（1.6）。
 */
@Data
public class CouponUpdateReq {

    @NotBlank(message = "优惠券名称不能为空")
    @Size(max = 50, message = "优惠券名称最多 50 个字符")
    private String couponName;

    @Min(value = 1, message = "类型只能是 1-满减 或 2-折扣")
    @Max(value = 2, message = "类型只能是 1-满减 或 2-折扣")
    @Schema(description = "类型：1-满减, 2-折扣")
    private Integer couponType;

    @Min(value = 1, message = "满减金额至少 1 分")
    private Long discountAmount;

    @DecimalMin(value = "0.01", message = "折扣率必须大于 0")
    @DecimalMax(value = "1.00", message = "折扣率不能超过 1")
    private BigDecimal discountRate;

    @Min(value = 0, message = "门槛不能为负")
    private Long thresholdAmount = 0L;

    @Schema(description = "适用业务：1-咖啡, 2-正餐（NULL=全部）")
    private Integer bizType;

    private List<Long> productIds;

    private List<Long> categoryIds;

    @Min(value = 1, message = "有效天数至少 1")
    private Integer validityDays = 30;

    @Schema(description = "状态：0-停用, 1-启用")
    private Integer status = 1;

    private Integer sortOrder = 0;
}
