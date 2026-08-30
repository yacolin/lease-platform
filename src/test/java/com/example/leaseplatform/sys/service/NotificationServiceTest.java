package com.example.leaseplatform.sys.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.sys.dto.NotificationVO;
import com.example.leaseplatform.sys.entity.SysNotification;
import com.example.leaseplatform.sys.mapper.SysNotificationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 通知中心单元测试。
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private SysNotificationMapper notificationMapper;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationMapper);
    }

    @Test
    void send_shouldInsertWithMockSent() {
        service.send(1L, NotificationService.TYPE_AUDIT_PASS, "企业审核通过", "您的企业已通过审核");

        org.mockito.ArgumentCaptor<SysNotification> captor =
                org.mockito.ArgumentCaptor.forClass(SysNotification.class);
        verify(notificationMapper).insert(captor.capture());
        SysNotification n = captor.getValue();
        assertThat(n.getUserId()).isEqualTo(1L);
        assertThat(n.getNotificationType()).isEqualTo(NotificationService.TYPE_AUDIT_PASS);
        assertThat(n.getSendStatus()).isEqualTo(1); // mock 发送成功
        assertThat(n.getIsRead()).isZero();
    }

    @Test
    void myNotifications_shouldReturnPaged() {
        when(notificationMapper.selectPage(any(IPage.class), any(Wrapper.class))).thenAnswer(inv -> {
            Page<SysNotification> p = inv.getArgument(0);
            SysNotification n = new SysNotification();
            n.setId(1L);
            n.setTitle("通知");
            p.setRecords(List.of(n));
            p.setTotal(1);
            return p;
        });

        PageResult<NotificationVO> result = service.myNotifications(1L, 1, 10, null);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.list().get(0).getTitle()).isEqualTo("通知");
    }

    @Test
    void unreadCount_shouldReturn() {
        when(notificationMapper.selectCount(any(Wrapper.class))).thenReturn(3L);

        assertThat(service.unreadCount(1L)).isEqualTo(3L);
    }

    @Test
    void markRead_notOwn_should404() {
        SysNotification other = new SysNotification();
        other.setId(1L);
        other.setUserId(99L);
        when(notificationMapper.selectById(1L)).thenReturn(other);

        assertThatThrownBy(() -> service.markRead(1L, 1L))
                .isInstanceOf(BizException.class)
                .hasMessage("通知不存在");
        verify(notificationMapper, never()).updateById(any(SysNotification.class));
    }

    @Test
    void markRead_shouldUpdate() {
        SysNotification n = new SysNotification();
        n.setId(1L);
        n.setUserId(1L);
        n.setIsRead(0);
        when(notificationMapper.selectById(1L)).thenReturn(n);

        service.markRead(1L, 1L);

        assertThat(n.getIsRead()).isEqualTo(1);
        verify(notificationMapper).updateById(n);
    }

    @Test
    void markAllRead_shouldBatchUpdate() {
        service.markAllRead(1L);

        verify(notificationMapper).update(any(), any(Wrapper.class));
    }
}
