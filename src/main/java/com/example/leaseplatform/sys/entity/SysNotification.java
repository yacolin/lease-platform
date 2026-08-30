package com.example.leaseplatform.sys.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内通知（sys_notifications）。
 * notification_type：1-审核通过, 2-审核拒绝, 3-充值成功, 4-订单完成, 5-预约成功, 6-员工邀请, 7-其他；
 * send_status：0-待发送, 1-发送成功, 2-发送失败；is_read：0-未读, 1-已读。
 * 自增 ID（通知记录量小，见 db/README.md 主键 ID 策略）。
 */
@Data
@TableName("sys_notifications")
public class SysNotification {

    /** 自增 ID（通知记录） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收用户 ID */
    private Long userId;

    /** 通知类型：1-审核通过, 2-审核拒绝, 3-充值成功, 4-订单完成, 5-预约成功, 6-员工邀请, 7-其他 */
    private Integer notificationType;

    /** 通知标题 */
    private String title;

    /** 通知内容 */
    private String content;

    /** 微信模板 ID（模板消息预留；未配置时 mock 发送成功） */
    private String templateId;

    /** 发送状态：0-待发送, 1-发送成功, 2-发送失败 */
    private Integer sendStatus;

    /** 发送时间 */
    private LocalDateTime sendTime;

    /** 是否已读：0-未读, 1-已读 */
    private Integer isRead;

    private LocalDateTime createdAt;
}
