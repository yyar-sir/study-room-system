package com.wzbc.studyroom.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wzbc.studyroom.common.BizException;
import com.wzbc.studyroom.common.ErrorCode;
import com.wzbc.studyroom.entity.Announcement;
import com.wzbc.studyroom.entity.Reservation;
import com.wzbc.studyroom.entity.User;
import com.wzbc.studyroom.mapper.AnnouncementMapper;
import com.wzbc.studyroom.mapper.ReservationMapper;
import com.wzbc.studyroom.mapper.UserMapper;
import com.wzbc.studyroom.service.AnnouncementService;
import com.wzbc.studyroom.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnnouncementServiceImpl implements AnnouncementService {

    private final AnnouncementMapper announcementMapper;
    private final NotificationService notificationService;
    private final UserMapper userMapper;
    private final ReservationMapper reservationMapper;

    /** 1 全平台 */
    private static final int SCOPE_ALL = 1;
    /** 1 已发布 */
    private static final int STATUS_PUBLISHED = 1;

    /** 2 自习室用户 */
    private static final int ROLE_USER = 2;
    /** 1 账号启用 */
    private static final int USER_ENABLED = 1;

    /** 单次推送的收件人上限，防止一条全平台公告把消息表写爆 */
    private static final int MAX_RECIPIENTS = 500;

    @Override
    public List<Announcement> listPublished(Long storeId) {
        // 用户要看的是「全平台公告 + 本门店公告」两类，
        // 只按 storeId 过滤会把全平台公告漏掉。
        LambdaQueryWrapper<Announcement> w = new LambdaQueryWrapper<Announcement>()
                .eq(Announcement::getStatus, STATUS_PUBLISHED)
                .orderByDesc(Announcement::getPublishTime)
                .last("LIMIT 50");
        if (storeId != null) {
            w.and(x -> x.eq(Announcement::getScope, SCOPE_ALL)
                    .or(y -> y.eq(Announcement::getStoreId, storeId)));
        }
        return announcementMapper.selectList(w);
    }

    @Override
    public List<Announcement> listForManage(Long storeId, boolean includeOffline) {
        LambdaQueryWrapper<Announcement> w = new LambdaQueryWrapper<Announcement>()
                .eq(storeId != null, Announcement::getStoreId, storeId)
                .orderByDesc(Announcement::getPublishTime)
                .last("LIMIT 100");
        if (!includeOffline) {
            w.eq(Announcement::getStatus, STATUS_PUBLISHED);
        }
        return announcementMapper.selectList(w);
    }

    @Override
    public Announcement getById(Long id) {
        Announcement a = id == null ? null : announcementMapper.selectById(id);
        if (a == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "公告不存在");
        }
        return a;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Announcement save(Announcement a) {
        if (a.getTitle() == null || a.getTitle().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "公告标题不能为空");
        }
        if (a.getContent() == null || a.getContent().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "公告内容不能为空");
        }
        if (a.getScope() == null) {
            a.setScope(2);      // 默认本门店
        }
        if (a.getScope() == SCOPE_ALL) {
            a.setStoreId(null); // 全平台公告不绑定门店，避免语义重复
        }
        if (a.getId() == null) {
            if (a.getStatus() == null) {
                a.setStatus(STATUS_PUBLISHED);
            }
            if (a.getPublishTime() == null) {
                a.setPublishTime(LocalDateTime.now());
            }
            announcementMapper.insert(a);
            if (STATUS_PUBLISHED == a.getStatus()) {
                notifyRecipients(a);
            }
        } else {
            Announcement old = getById(a.getId());
            announcementMapper.updateById(a);
            // 只在「草稿/已下架 → 已发布」这个跃迁上推送一次，
            // 否则每次改个错别字都会给全店再发一遍通知。
            Announcement now = announcementMapper.selectById(a.getId());
            boolean wasPublished = old.getStatus() != null && old.getStatus() == STATUS_PUBLISHED;
            boolean isPublished = now != null && now.getStatus() != null && now.getStatus() == STATUS_PUBLISHED;
            if (!wasPublished && isPublished) {
                notifyRecipients(now);
            }
        }
        return announcementMapper.selectById(a.getId());
    }

    /**
     * 把公告作为站内消息推送给相关用户。
     *
     * <p>此前公告只写 announcement 表，消息中心的「公告」标签页查的是
     * notification 表里 type=announce 的记录，而没有任何代码发出过这种消息——
     * 结果那个标签页永远是空的。公告必须同时落两处：一处供页面展示，
     * 一处供消息中心提醒。
     *
     * <p>收件人不到 announcement 表里找，而是按「与这家门店有关系」判定；
     * 且推送失败不影响公告本身（sendToMany 内部逐条隔离异常）。
     */
    private void notifyRecipients(Announcement a) {
        List<Long> userIds = targetUserIds(a);
        if (userIds.isEmpty()) {
            log.info("公告 {} 没有匹配到需要推送的用户", a.getId());
            return;
        }
        notificationService.sendToMany(userIds, NotificationService.TYPE_ANNOUNCE,
                "门店公告：" + a.getTitle(),
                a.getContent(),
                "/user/messages");
    }

    /** 计算收件人：全平台公告发给所有用户；门店公告发给「常用门店是本店」或「在本店预约过」的用户 */
    private List<Long> targetUserIds(Announcement a) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();

        if (a.getScope() != null && a.getScope() == SCOPE_ALL) {
            userMapper.selectList(new LambdaQueryWrapper<User>()
                            .eq(User::getRole, ROLE_USER)
                            .eq(User::getStatus, USER_ENABLED))
                    .forEach(u -> ids.add(u.getId()));
            return limit(ids);
        }

        Long storeId = a.getStoreId();
        if (storeId == null) {
            return new ArrayList<>();
        }

        // ① 把该店设为常用门店的用户
        userMapper.selectList(new LambdaQueryWrapper<User>()
                        .eq(User::getRole, ROLE_USER)
                        .eq(User::getStatus, USER_ENABLED)
                        .eq(User::getStoreId, storeId))
                .forEach(u -> ids.add(u.getId()));

        // ② 在该店有过预约的用户。
        //    只按 ① 判定会漏掉大多数人——用户不会特意去设「常用门店」，
        //    但下过单就说明他关心这家店的通知。
        reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                        .select(Reservation::getUserId)
                        .eq(Reservation::getStoreId, storeId))
                .forEach(r -> {
                    if (r.getUserId() != null) {
                        ids.add(r.getUserId());
                    }
                });

        return limit(ids);
    }

    private List<Long> limit(LinkedHashSet<Long> ids) {
        List<Long> list = new ArrayList<>(ids);
        if (list.size() > MAX_RECIPIENTS) {
            return list.subList(0, MAX_RECIPIENTS);
        }
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void offline(Long id) {
        getById(id);
        Announcement patch = new Announcement();
        patch.setId(id);
        patch.setStatus(0);
        announcementMapper.updateById(patch);
    }
}
