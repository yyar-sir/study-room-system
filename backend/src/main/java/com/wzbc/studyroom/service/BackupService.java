package com.wzbc.studyroom.service;

import com.wzbc.studyroom.entity.BackupRecord;

import java.util.List;

public interface BackupService {

    /**
     * 执行一次数据库备份。
     *
     * <p>刻意<b>不抛异常</b>：备份失败也要落一条 status=0 的记录并返回，
     * 这样经营端能看到"什么时候尝试过、失败原因是什么"。
     * 如果直接抛异常，失败就完全没痕迹了。
     *
     * @param backupType 1手动 2定时
     */
    BackupRecord backup(Long operatorId, int backupType);

    List<BackupRecord> list();
}
