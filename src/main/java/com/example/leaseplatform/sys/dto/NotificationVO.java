package com.example.leaseplatform.sys.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 站内通知视图对象。
 */
@Data
public class NotificationVO {

    private Long id;

    /** 通知类型：1-审核通过, 2-审核拒绝, 3-充值成功, 4-订单完成, 5-预约成功, 6-员工邀请, 7-其他 */
    @Schema(description = "通知类型：1-审核通过, 2-审核拒绝, 3-充值成功, 4-订单完成, 5-预约成功, 6-员工邀请, 7-其他")
    private Integer notificationType;

    private String title;

    private String content;

    /** 发送时间（epoch 毫秒时间戳） */
    private Long sendTime;

    /** 是否已读：0-未读, 1-已读 */
    @Schema(description = "是否已读：0-未读, 1-已读")
    private Integer isRead;

    /** 创建时间（epoch 毫秒时间戳） */
    private Long createdAt;
}
