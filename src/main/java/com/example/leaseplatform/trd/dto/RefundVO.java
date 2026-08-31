package com.example.leaseplatform.trd.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 退款单视图对象（1.2）。
 */
@Data
public class RefundVO {

    private Long id;

    /** 退款单编号 */
    private String refundNo;

    /** 关联支付单 ID */
    private Long paymentId;

    /** 退款用户 ID */
    private Long userId;

    /** 业务类型（同 trd_payments） */
    @Schema(description = "业务类型：1-充值, 2-咖啡订单, 3-正餐预订, 4-会员购买")
    private Integer bizType;

    /** 业务单 ID */
    private Long bizId;

    /** 退款金额（分） */
    private Long refundAmount;

    /** 退款方式：1-原路余额, 2-原路微信 */
    @Schema(description = "退款方式：1-原路余额, 2-原路微信")
    private Integer refundMethod;

    /** 退款状态：0-处理中, 1-成功, 2-失败 */
    @Schema(description = "退款状态：0-处理中, 1-成功, 2-失败")
    private Integer status;

    /** 退款原因 */
    private String refundReason;

    /** 退款完成时间（epoch 毫秒） */
    private Long refundedAt;

    /** 创建时间（epoch 毫秒） */
    private Long createdAt;
}
