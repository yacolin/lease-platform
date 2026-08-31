package com.example.leaseplatform.ord.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 订单状态历史视图对象（1.2）。
 */
@Data
public class OrderStatusHistoryVO {

    private Long id;

    /** 业务单 ID（咖啡订单或正餐预订） */
    private Long orderId;

    /** 业务类型：1-咖啡订单, 2-正餐预订 */
    @Schema(description = "业务类型：1-咖啡订单, 2-正餐预订")
    private Integer bizType;

    /** 变更前状态（初始状态为 NULL） */
    private Integer fromStatus;

    /** 变更后状态 */
    private Integer toStatus;

    /** 操作人 ID */
    private Long operatorId;

    /** 操作人类型：1-用户, 2-商家/系统 */
    @Schema(description = "操作人类型：1-用户, 2-商家/系统")
    private Integer operatorType;

    /** 变更原因 */
    private String reason;

    /** 变更时间（epoch 毫秒） */
    private Long createdAt;
}
