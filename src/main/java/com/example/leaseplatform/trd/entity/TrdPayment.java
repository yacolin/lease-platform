package com.example.leaseplatform.trd.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 支付单（trd_payments，1.2 交易可靠性）：统一支付事实，与业务单（biz_type + biz_id）解耦。
 * biz_type：1-充值, 2-咖啡订单, 3-正餐预订, 4-会员购买；
 * payment_method：1-余额支付, 2-微信支付；
 * payment_channel：1-微信JSAPI, 2-余额, 3-mock直充；
 * status：0-待支付, 1-成功, 2-失败, 3-部分退款, 4-已退款。
 * 支付成功/退款由状态乐观更新保证幂等（roadmap 1.2「支付幂等」）。
 */
@Data
@TableName("trd_payments")
public class TrdPayment {

    /** 雪花 ID（资金流水，见 db/README.md 主键 ID 策略） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 支付单编号（唯一） */
    private String paymentNo;

    /** 支付用户 ID */
    private Long userId;

    /** 业务类型：1-充值, 2-咖啡订单, 3-正餐预订, 4-会员购买 */
    private Integer bizType;

    /** 业务单 ID（充值记录/订单/预订/购买记录） */
    private Long bizId;

    /** 支付金额（分） */
    private Long amount;

    /** 支付方式：1-余额支付, 2-微信支付 */
    private Integer paymentMethod;

    /** 支付渠道：1-微信JSAPI, 2-余额, 3-mock直充 */
    private Integer paymentChannel;

    /** 支付状态：0-待支付, 1-成功, 2-失败, 3-部分退款, 4-已退款 */
    private Integer status;

    /** 商户订单号（唯一） */
    private String outTradeNo;

    /** 支付渠道交易号（唯一，余额支付为 NULL） */
    private String transactionId;

    /** 支付时间 */
    private LocalDateTime paidAt;

    /** 待支付过期时间（超时自动关闭） */
    private LocalDateTime expiredAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
