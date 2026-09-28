package com.wzbc.studyroom.controller;

import com.wzbc.studyroom.common.Result;
import com.wzbc.studyroom.entity.SystemConfig;
import com.wzbc.studyroom.security.RequireRole;
import com.wzbc.studyroom.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 运营规则与系统配置（仅经营者，role = 0）。
 *
 * <p>这些值直接决定预约行为（可提前几天、多久前必须签到、爽约怎么罚），
 * 改完立即生效 —— 业务代码每次都从本表读取，不缓存、不重启。
 * 前台无权查看与修改：那属于经营决策，不是前台日常操作。
 */
@RestController
@RequestMapping("/api/owner/configs")
@RequireRole(0)
@RequiredArgsConstructor
public class SystemConfigController {

    private final SystemConfigService configService;

    /**
     * 配置列表。
     *
     * @param storeId 不传表示全局配置（storeId=0）
     * @param group   rule 运营规则 / remind 提醒 / system 系统参数
     */
    @GetMapping
    public Result<List<SystemConfig>> list(@RequestParam(required = false) Long storeId,
                                           @RequestParam(required = false) String group) {
        return Result.ok(configService.list(storeId, group));
    }

    /** 新增或更新一条配置（upsert） */
    @PostMapping
    public Result<Void> save(@RequestBody Map<String, Object> body) {
        Object storeId = body.get("storeId");
        Long sid = null;
        if (storeId != null && !"".equals(storeId)) {
            sid = Long.valueOf(storeId.toString());
        }
        configService.save(
                sid,
                str(body.get("configKey")),
                str(body.get("configValue")),
                str(body.get("configGroup")),
                str(body.get("description")));
        return Result.ok();
    }

    private String str(Object o) {
        return o == null ? null : o.toString();
    }
}
