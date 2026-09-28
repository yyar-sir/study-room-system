package com.wzbc.studyroom.service;

import com.wzbc.studyroom.entity.Notification;

import java.util.List;

/**
 * 站内消息与提醒。
 *
 * <p>消息不是独立功能，而是<b>业务动作的副作用</b>：签到扣次、套餐到期、爽约判罚、
 * 违规处理都在各自流程里调用本服务。这样"消息中心"里的每一条都能对应到一件真实发生过的事。
 */
public interface NotificationService {

    void send(Long userId, String type, String title, String content, String link);

    /**
     * 批量发送（用于公告推送给门店全体用户）。
     *
     * <p>逐条发送并复用 {@link #send} 的失败隔离：给某一个用户写失败
     * 不会中断其余用户。门店级用户量下够用；若要面向全平台大规模推送，
     * 应换成批量 insert。
     */
    void sendToMany(List<Long> userIds, String type, String title, String content, String link);

    List<Notification> mine(Long userId, String type);

    int unreadCount(Long userId);

    void markRead(Long userId, Long id);

    void markAllRead(Long userId);

    // 消息类型常量
    String TYPE_PLAN_EXPIRE = "plan_expire";
    String TYPE_RESERVE_REMIND = "reserve_remind";
    String TYPE_VIOLATION = "violation";
    String TYPE_ANNOUNCE = "announce";
    String TYPE_SYSTEM = "system";
}
