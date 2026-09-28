package com.wzbc.studyroom.controller;

import com.wzbc.studyroom.common.Result;
import com.wzbc.studyroom.entity.Notification;
import com.wzbc.studyroom.security.UserContext;
import com.wzbc.studyroom.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 站内消息中心（三类角色共用）。
 *
 * <p>不限定角色：经营者/前台也会收到系统消息。但所有查询都强制带
 * {@code user_id = 当前登录用户}，无法越权读别人的消息。
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public Result<List<Notification>> list(@RequestParam(required = false) String type) {
        return Result.ok(notificationService.mine(UserContext.userId(), type));
    }

    /** 未读数，供顶栏红点轮询 */
    @GetMapping("/unread-count")
    public Result<Integer> unreadCount() {
        return Result.ok(notificationService.unreadCount(UserContext.userId()));
    }

    @PostMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(UserContext.userId(), id);
        return Result.ok();
    }

    @PostMapping("/read-all")
    public Result<Void> markAllRead() {
        notificationService.markAllRead(UserContext.userId());
        return Result.ok();
    }
}
