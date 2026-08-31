package com.example.leaseplatform.trd.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 支付单视图对象（1.2）。
 */
@Data
public class PaymentVO {

    private Long id;

    /** 支付单编号 */
    private String paymentNo;

    /** 支付用户 ID */
    private Long userId;

    /** 业务类型：1-充值, 2-咖啡订单, 3-正餐预订, 4-会员购买 */
    @Schema(description = "业务类型：1-充值, 2-咖啡订单, 3-正餐预订, 4-会员购买")
    private Integer bizType;

    /** 业务单 ID */
    private Long bizId;

    /** 支付金额（分） */
    private Long amount;

    /** 支付方式：1-余额支付, 2-微信支付 */
    @Schema(description = "支付方式：1-余额支付, 2-微信支付")
    private Integer paymentMethod;

    /** 支付渠道：1-微信JSAPI, 2-余额, 3-mock直充 */
    @Schema(description = "支付渠道：1-微信JSAPI, 2-余额, 3-mock直充")
    private Integer paymentChannel;

    /** 支付状态：0-待支付, 1-成功, 2-失败, 3-部分退款, 4-已退款 */
    @Schema(description = "支付状态：0-待支付, 1-成功, 2-失败, 3-部分退款, 4-已退款")
    private Integer status;

    /** 商户订单号 */
    private String outTradeNo;

    /** 支付渠道交易号 */
    private String transactionId;

    /** 支付时间（epoch 毫秒） */
    private Long paidAt;

    /** 创建时间（epoch 毫秒） */
    private Long createdAt;
}
