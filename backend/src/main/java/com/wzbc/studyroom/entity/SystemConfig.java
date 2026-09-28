package com.wzbc.studyroom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 系统与运营规则配置 */
@Data
@TableName("system_config")
public class SystemConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 0 表示全局配置（刻意不用 NULL，见 init.sql 中该表说明） */
    private Long storeId;
    /** rule 运营规则 / remind 提醒 / system 系统参数 */
    private String configGroup;
    private String configKey;
    private String configValue;
    private String description;

    private LocalDateTime updatedAt;
}
