package com.example.leaseplatform.mkt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户优惠券（mkt_user_coupons，1.6）：领取时对券规则做快照
 * （名称/类型/金额/折扣率/门槛/范围），规则可改、用户券快照不变。
 * status：0-未使用, 1-已使用, 2-已过期；order_id 绑定使用订单。
 * 雪花 ID（资金/营销流水，见 db/README.md 主键 ID 策略）。
 */
@Data
@TableName("mkt_user_coupons")
public class MktUserCoupon {

    /** 状态 */
    public static final int STATUS_UNUSED = 0;
    public static final int STATUS_USED = 1;
    public static final int STATUS_EXPIRED = 2;

    /** 雪花 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 优惠券模板 ID */
    private Long couponId;

    /** 用户 ID */
    private Long userId;

    /** 券名称（快照） */
    private String couponName;

    /** 类型：1-满减, 2-折扣（快照） */
    private Integer couponType;

    /** 满减金额（分，快照） */
    private Long discountAmount;

    /** 折扣率（快照） */
    private BigDecimal discountRate;

    /** 使用门槛（分，快照） */
    private Long thresholdAmount;

    /** 适用业务（快照） */
    private Integer bizType;

    /** 指定商品 ID JSON（快照） */
    private String productIds;

    /** 指定分类 ID JSON（快照） */
    private String categoryIds;

    /** 状态：0-未使用, 1-已使用, 2-已过期 */
    private Integer status;

    /** 使用订单 ID */
    private Long orderId;

    /** 使用时间 */
    private LocalDateTime usedAt;

    /** 过期时间 */
    private LocalDateTime expireAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
