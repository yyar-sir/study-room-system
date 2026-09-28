package com.wzbc.studyroom.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wzbc.studyroom.entity.Notification;
import com.wzbc.studyroom.mapper.NotificationMapper;
import com.wzbc.studyroom.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;

    @Override
    public void send(Long userId, String type, String title, String content, String link) {
        try {
            Notification n = new Notification();
            n.setUserId(userId);
            n.setType(type);
            n.setTitle(title);
            n.setContent(content);
            n.setLink(link);
            n.setIsRead(0);
            n.setCreatedAt(LocalDateTime.now());
            notificationMapper.insert(n);
        } catch (Exception e) {
            // 消息是业务动作的副作用：发消息失败不应让主流程（签到/扣次/判罚）回滚
            log.warn("站内消息发送失败 userId={} type={} : {}", userId, type, e.getMessage());
        }
    }

    @Override
    public void sendToMany(List<Long> userIds, String type, String title, String content, String link) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        for (Long userId : userIds) {
            send(userId, type, title, content, link);
        }
    }

    @Override
    public List<Notification> mine(Long userId, String type) {
        LambdaQueryWrapper<Notification> w = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .orderByDesc(Notification::getCreatedAt)
                .last("LIMIT 100");
        if (type != null && !type.isBlank()) {
            w.eq(Notification::getType, type);
        }
        return notificationMapper.selectList(w);
    }

    @Override
    public int unreadCount(Long userId) {
        Long c = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0));
        return c == null ? 0 : c.intValue();
    }

    @Override
    public void markRead(Long userId, Long id) {
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getId, id)
                .eq(Notification::getUserId, userId)     // 只能标记自己的消息
                .set(Notification::getIsRead, 1));
    }

    @Override
    public void markAllRead(Long userId) {
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1));
    }
}
