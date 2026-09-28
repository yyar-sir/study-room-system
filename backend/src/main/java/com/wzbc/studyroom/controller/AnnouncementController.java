package com.wzbc.studyroom.controller;

import com.wzbc.studyroom.common.BizException;
import com.wzbc.studyroom.common.ErrorCode;
import com.wzbc.studyroom.common.Result;
import com.wzbc.studyroom.entity.Announcement;
import com.wzbc.studyroom.security.RequireRole;
import com.wzbc.studyroom.security.UserContext;
import com.wzbc.studyroom.service.AnnouncementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 公告：发布由前台/经营者负责，查看对所有人开放（含未登录）。
 *
 * <p>权限规则（服务端强制）：
 * <ul>
 *   <li>前台（role 1）只能发布<b>本门店</b>公告，scope 被强制改写为 2、store_id 强制为自己门店
 *       —— 否则前台就能给全平台发公告；</li>
 *   <li>经营者（role 0）可发全平台公告，也可指定门店。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    /** 公开：某门店可见的公告（全平台 + 本门店） */
    @GetMapping("/public/announcements")
    public Result<List<Announcement>> published(@RequestParam(required = false) Long storeId) {
        return Result.ok(announcementService.listPublished(storeId));
    }

    /** 管理侧列表（含已下架）。前台只看本门店。 */
    @GetMapping("/staff/announcements")
    @RequireRole({0, 1})
    public Result<List<Announcement>> manage(@RequestParam(required = false) Long storeId,
                                             @RequestParam(defaultValue = "true") boolean includeOffline) {
        Long scope = UserContext.isOwner() ? storeId : UserContext.storeId();
        if (!UserContext.isOwner() && scope == null) {
            throw new BizException(ErrorCode.FORBIDDEN, "当前账号未分配门店");
        }
        return Result.ok(announcementService.listForManage(scope, includeOffline));
    }

    /** 发布 / 编辑公告 */
    @PostMapping("/staff/announcements")
    @RequireRole({0, 1})
    public Result<Announcement> save(@RequestBody Announcement a) {
        if (!UserContext.isOwner()) {
            // 前台不得越权发全平台公告
            a.setScope(2);
            a.setStoreId(UserContext.storeId());
            if (a.getStoreId() == null) {
                throw new BizException(ErrorCode.FORBIDDEN, "当前账号未分配门店，无法发布公告");
            }
        }
        a.setPublisherId(UserContext.userId());
        return Result.ok(announcementService.save(a));
    }

    /** 下架公告 */
    @PostMapping("/staff/announcements/{id}/offline")
    @RequireRole({0, 1})
    public Result<Void> offline(@PathVariable Long id) {
        Announcement exist = announcementService.getById(id);
        if (!UserContext.isOwner()) {
            Long own = UserContext.storeId();
            if (own == null || !own.equals(exist.getStoreId())) {
                throw new BizException(ErrorCode.FORBIDDEN, "只能下架本门店的公告");
            }
        }
        announcementService.offline(id);
        return Result.ok();
    }
}
