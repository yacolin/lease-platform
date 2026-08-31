package com.example.leaseplatform.ord.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单状态历史（ord_order_status_history，1.2 交易可靠性）：
 * 咖啡订单/正餐预订状态流转全量留痕，业务事实可追踪（roadmap 1.2.3）。
 * order_id 为业务单 ID（咖啡订单 ord_orders.id 或 正餐预订 ord_meal_reservations.id）；
 * biz_type：1-咖啡订单, 2-正餐预订；
 * operator_type：1-用户, 2-商家/系统。
 */
@Data
@TableName("ord_order_status_history")
public class OrdOrderStatusHistory {

    /** 雪花 ID（日志/流水表） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 业务单 ID（咖啡订单或正餐预订） */
    private Long orderId;

    /** 业务类型：1-咖啡订单, 2-正餐预订 */
    private Integer bizType;

    /** 变更前状态（初始状态为 NULL） */
    private Integer fromStatus;

    /** 变更后状态 */
    private Integer toStatus;

    /** 操作人 ID（用户或管理员） */
    private Long operatorId;

    /** 操作人类型：1-用户, 2-商家/系统 */
    private Integer operatorType;

    /** 变更原因 */
    private String reason;

    /** 变更时间 */
    private LocalDateTime createdAt;
}
