package com.example.leaseplatform.mkt.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 优惠券模板视图对象（1.6）。
 */
@Data
public class CouponVO {

    private Long id;

    private String couponName;

    @Schema(description = "类型：1-满减, 2-折扣")
    private Integer couponType;

    /** 满减金额（分） */
    private Long discountAmount;

    /** 折扣率（如 0.90） */
    private BigDecimal discountRate;

    /** 使用门槛（分，0=无门槛） */
    private Long thresholdAmount;

    @Schema(description = "适用业务：1-咖啡, 2-正餐（NULL=全部）")
    private Integer bizType;

    /** 指定商品 ID（NULL=全部） */
    private Object productIds;

    /** 指定分类 ID（NULL=全部） */
    private Object categoryIds;

    /** 领取后有效天数 */
    private Integer validityDays;

    @Schema(description = "状态：0-停用, 1-启用")
    private Integer status;

    private Integer sortOrder;

    /** 创建时间（epoch 毫秒） */
    private Long createdAt;

    /** 更新时间（epoch 毫秒） */
    private Long updatedAt;
}
