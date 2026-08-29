package com.example.leaseplatform.usr.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 会员购买记录（usr_member_purchases）：企业购买会员服务包的记录。
 * payment_status：0-待支付, 1-支付成功, 2-支付失败, 3-已退款。
 */
@Data
@TableName("usr_member_purchases")
public class UsrMemberPurchase {

    /** 雪花 ID（购买记录，数据积累，见 db/README.md 主键 ID 策略） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 购买编号（唯一） */
    private String purchaseNo;

    /** 企业 ID */
    private Long enterpriseId;

    /** 会员等级 ID（usr_member_levels.id） */
    private Long memberLevelId;

    /** 原价 */
    private BigDecimal originalPrice;

    /** 实付价格 */
    private BigDecimal payPrice;

    /** 支付方式：1-微信支付, 2-余额支付 */
    private Integer paymentMethod;

    /** 微信支付交易号 */
    private String transactionId;

    /** 商户订单号 */
    private String outTradeNo;

    /** 开始日期 */
    private LocalDate startDate;

    /** 结束日期（1 年） */
    private LocalDate endDate;

    /** 支付状态：0-待支付, 1-支付成功, 2-支付失败, 3-已退款 */
    private Integer paymentStatus;

    /** 支付时间 */
    private LocalDateTime paidAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
