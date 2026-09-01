package com.example.leaseplatform.mkt.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 我的优惠券视图对象（1.6，快照 + 使用状态）。
 */
@Data
public class UserCouponVO {

    private Long id;

    /** 优惠券模板 ID */
    private Long couponId;

    private String couponName;

    @Schema(description = "类型：1-满减, 2-折扣")
    private Integer couponType;

    private Long discountAmount;

    private BigDecimal discountRate;

    /** 使用门槛（分） */
    private Long thresholdAmount;

    @Schema(description = "适用业务：1-咖啡, 2-正餐（NULL=全部）")
    private Integer bizType;

    @Schema(description = "状态：0-未使用, 1-已使用, 2-已过期")
    private Integer status;

    /** 使用订单 ID */
    private Long orderId;

    /** 过期时间（epoch 毫秒） */
    private Long expireAt;

    /** 领取时间（epoch 毫秒） */
    private Long createdAt;
}
