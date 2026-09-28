package com.wzbc.studyroom.service;

import com.wzbc.studyroom.entity.Announcement;

import java.util.List;

public interface AnnouncementService {

    /**
     * 用户可见的公告：全平台公告 + 本门店公告，只含已发布。
     */
    List<Announcement> listPublished(Long storeId);

    /** 管理侧公告列表（含已下架），按门店过滤；storeId 为 null 表示不过滤 */
    List<Announcement> listForManage(Long storeId, boolean includeOffline);

    Announcement getById(Long id);

    /** 发布公告；id 为空则新建 */
    Announcement save(Announcement a);

    /** 下架（保留记录，用户端不再展示） */
    void offline(Long id);
}
