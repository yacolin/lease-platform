package com.example.leaseplatform.sys.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.common.TimeUtil;
import com.example.leaseplatform.sys.dto.NotificationVO;
import com.example.leaseplatform.sys.entity.SysNotification;
import com.example.leaseplatform.sys.mapper.SysNotificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知中心：站内通知（微信模板消息预留）。
 * 各业务域（审核/充值/订单/预约/员工邀请）通过 {@link #send} 触发通知；
 * 微信模板消息真实发送待接入（template_id 预留），当前 mock 发送成功。
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    /** 通知类型 */
    public static final int TYPE_AUDIT_PASS = 1;
    public static final int TYPE_AUDIT_REJECT = 2;
    public static final int TYPE_RECHARGE = 3;
    public static final int TYPE_ORDER_DONE = 4;
    public static final int TYPE_RESERVATION = 5;
    public static final int TYPE_INVITE = 6;
    public static final int TYPE_OTHER = 7;

    private final SysNotificationMapper notificationMapper;

    /** 发送站内通知（微信模板消息预留：未配置模板时 mock 发送成功） */
    public void send(Long userId, int type, String title, String content) {
        send(userId, type, title, content, null);
    }

    /** 发送站内通知（可携带微信模板 ID） */
    public void send(Long userId, int type, String title, String content, String templateId) {
        SysNotification notification = new SysNotification();
        notification.setUserId(userId);
        notification.setNotificationType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setTemplateId(templateId);
        // 微信模板消息预留：真实发送待接入（WechatTemplateService），当前 mock 成功
        notification.setSendStatus(1);
        notification.setSendTime(LocalDateTime.now());
        notification.setIsRead(0);
        notificationMapper.insert(notification);
    }

    /** 我的通知（分页，可仅未读） */
    public PageResult<NotificationVO> myNotifications(Long userId, int page, int size, Boolean unreadOnly) {
        Page<SysNotification> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 1000));
        notificationMapper.selectPage(p, new LambdaQueryWrapper<SysNotification>()
                .eq(SysNotification::getUserId, userId)
                .eq(Boolean.TRUE.equals(unreadOnly), SysNotification::getIsRead, 0)
                .orderByDesc(SysNotification::getId));
        return PageResult.of(p.getTotal(), p.getRecords().stream().map(this::toVO).toList());
    }

    /** 未读数 */
    public long unreadCount(Long userId) {
        Long count = notificationMapper.selectCount(new LambdaQueryWrapper<SysNotification>()
                .eq(SysNotification::getUserId, userId)
                .eq(SysNotification::getIsRead, 0));
        return count == null ? 0 : count;
    }

    /** 标记单条已读 */
    public void markRead(Long userId, Long id) {
        SysNotification notification = notificationMapper.selectById(id);
        if (notification == null || !notification.getUserId().equals(userId)) {
            throw BizException.notFound("通知不存在");
        }
        if (notification.getIsRead() == null || notification.getIsRead() != 1) {
            notification.setIsRead(1);
            notificationMapper.updateById(notification);
        }
    }

    /** 全部已读 */
    public void markAllRead(Long userId) {
        notificationMapper.update(null, new LambdaUpdateWrapper<SysNotification>()
                .eq(SysNotification::getUserId, userId)
                .eq(SysNotification::getIsRead, 0)
                .set(SysNotification::getIsRead, 1));
    }

    private NotificationVO toVO(SysNotification n) {
        NotificationVO vo = new NotificationVO();
        vo.setId(n.getId());
        vo.setNotificationType(n.getNotificationType());
        vo.setTitle(n.getTitle());
        vo.setContent(n.getContent());
        vo.setSendTime(TimeUtil.toEpochMillis(n.getSendTime()));
        vo.setIsRead(n.getIsRead());
        vo.setCreatedAt(TimeUtil.toEpochMillis(n.getCreatedAt()));
        return vo;
    }
}
