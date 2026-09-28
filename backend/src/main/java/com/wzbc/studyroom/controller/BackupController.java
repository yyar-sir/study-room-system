package com.wzbc.studyroom.controller;

import com.wzbc.studyroom.common.Result;
import com.wzbc.studyroom.entity.BackupRecord;
import com.wzbc.studyroom.security.RequireRole;
import com.wzbc.studyroom.security.UserContext;
import com.wzbc.studyroom.service.BackupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 数据备份（仅经营者，role = 0） */
@RestController
@RequestMapping("/api/owner/backup")
@RequireRole(0)
@RequiredArgsConstructor
public class BackupController {

    private final BackupService backupService;

    /**
     * 立即执行一次备份。
     * <p>返回体里的 status=0 表示本次备份失败，remark 里是原因 ——
     * 接口本身仍返回 200，因为"备份失败"是可预期的业务结果，不是系统故障。
     */
    @PostMapping
    public Result<BackupRecord> run() {
        return Result.ok(backupService.backup(UserContext.userId(), 1));
    }

    /** 备份记录列表 */
    @GetMapping
    public Result<List<BackupRecord>> list() {
        return Result.ok(backupService.list());
    }
}
