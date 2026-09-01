package com.example.leaseplatform.mkt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券模板（mkt_coupons，1.6 运营/营销）：
 * coupon_type 1-满减（discount_amount）/ 2-折扣（discount_rate）；
 * threshold_amount 使用门槛（0=无门槛）；biz_type 适用业务（1-咖啡, 2-正餐, NULL=全部）；
 * product_ids / category_ids 指定商品/分类（JSON，NULL=全部）；validity_days 领取后有效天数。
 * 配置表，自增 ID。
 */
@Data
@TableName("mkt_coupons")
public class MktCoupon {

    /** 类型 */
    public static final int TYPE_FULL_REDUCTION = 1; // 满减
    public static final int TYPE_DISCOUNT = 2;       // 折扣

    /** 状态 */
    public static final int STATUS_DISABLED = 0;
    public static final int STATUS_ENABLED = 1;

    /** 自增 ID（配置表，见 db/README.md 主键 ID 策略） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 优惠券名称 */
    private String couponName;

    /** 类型：1-满减, 2-折扣 */
    private Integer couponType;

    /** 满减金额（分，coupon_type=1） */
    private Long discountAmount;

    /** 折扣率（如 0.90 表示 9 折，coupon_type=2） */
    private BigDecimal discountRate;

    /** 使用门槛（分，满 X 可用；0=无门槛） */
    private Long thresholdAmount;

    /** 适用业务：1-咖啡, 2-正餐（NULL=全部） */
    private Integer bizType;

    /** 指定商品 ID JSON（NULL=全部） */
    private String productIds;

    /** 指定分类 ID JSON（NULL=全部） */
    private String categoryIds;

    /** 领取后有效天数 */
    private Integer validityDays;

    /** 状态：0-停用, 1-启用 */
    private Integer status;

    /** 排序权重（升序） */
    private Integer sortOrder;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
