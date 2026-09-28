package com.wzbc.studyroom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据备份记录。
 *
 * <p>刻意把「备份文件」与「备份记录」分开：文件落在磁盘上，
 * 记录落库。这样即使备份文件被误删，也能从记录里看出"什么时候备份过、
 * 本该有多大"，而不是完全没有痕迹。
 */
@Data
@TableName("backup_record")
public class BackupRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String fileName;
    private String filePath;
    private Long fileSize;
    /** 1手动 2定时 */
    private Integer backupType;
    /** 1成功 0失败 */
    private Integer status;
    private Long operatorId;
    /** 失败时记录原因，成功时记录简要说明 */
    private String remark;

    private LocalDateTime createdAt;
}
