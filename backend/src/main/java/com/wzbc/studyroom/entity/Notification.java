package com.wzbc.studyroom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内消息 / 到期与预约提醒。
 *
 * <p>⚠️ 已读字段名是 {@code isRead}（映射到 {@code is_read}）—— {@code read} 是 MySQL 保留字。
 */
@Data
@TableName("notification")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    /** plan_expire 套餐到期 / reserve_remind 预约提醒 / violation 违规 / announce 公告 / system 系统 */
    private String type;
    private String title;
    private String content;
    /** 点击跳转的前端路由 */
    private String link;
    /** 0未读 1已读 */
    private Integer isRead;

    private LocalDateTime createdAt;
}
