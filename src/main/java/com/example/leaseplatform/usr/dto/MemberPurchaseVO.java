package com.example.leaseplatform.usr.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 会员购买记录视图对象。
 */
@Data
public class MemberPurchaseVO {

    private Long id;

    /** 购买编号 */
    private String purchaseNo;

    /** 商户订单号（下单即生成，支付回调/查单用） */
    private String outTradeNo;

    /** 等级名称 */
    private String memberLevelName;

    /** 等级编码：BASIC / VIP / SVIP */
    private String memberLevelCode;

    /** 原价 */
    private BigDecimal originalPrice;

    /** 实付价格 */
    private BigDecimal payPrice;

    /** 支付方式：1-微信支付, 2-余额支付 */
    private Integer paymentMethod;

    /** 支付状态：0-待支付, 1-支付成功, 2-支付失败, 3-已退款 */
    private Integer paymentStatus;

    /** 生效开始日期 */
    private LocalDate startDate;

    /** 生效结束日期 */
    private LocalDate endDate;

    /** 支付时间（epoch 毫秒时间戳） */
    private Long paidAt;

    /** 下单时间（epoch 毫秒时间戳） */
    private Long createdAt;
}
