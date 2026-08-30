package com.example.leaseplatform.sys.controller;

import com.example.leaseplatform.common.ApiResponse;
import com.example.leaseplatform.common.PageResult;
import com.example.leaseplatform.security.UserContext;
import com.example.leaseplatform.sys.dto.NotificationVO;
import com.example.leaseplatform.sys.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 我的通知（小程序端 /api/v1/me/notifications/**，需登录）。
 */
@Tag(name = "myNotifications", description = "我的通知：站内通知：列表/未读数/已读（需登录）")
@RestController
@RequestMapping("/api/v1/me/notifications")
@RequiredArgsConstructor
public class MeNotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "我的通知分页（可仅未读）")
    @GetMapping
    public ApiResponse<PageResult<NotificationVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Boolean unreadOnly) {
        return ApiResponse.ok(notificationService.myNotifications(UserContext.getUserId(), page, size, unreadOnly));
    }

    @Operation(summary = "未读通知数")
    @GetMapping("/unread-count")
    public ApiResponse<Long> unreadCount() {
        return ApiResponse.ok(notificationService.unreadCount(UserContext.getUserId()));
    }

    @Operation(summary = "标记单条已读")
    @PutMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(UserContext.getUserId(), id);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "全部标记已读")
    @PutMapping("/read-all")
    public ApiResponse<Void> markAllRead() {
        notificationService.markAllRead(UserContext.getUserId());
        return ApiResponse.ok(null);
    }
}
