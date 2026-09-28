package com.wzbc.studyroom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 账号（自习室用户 / 前台管理员 / 自习室经营者 三角色合一）。
 *
 * <p>角色编码：<b>0 = 经营者，1 = 前台管理员，2 = 自习室用户</b>。
 * 三类角色的登录入口不同，但账号体系统一放在一张表里，
 * 避免三张几乎相同的表带来重复的登录、改密、状态管理逻辑。
 */
@Data
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 手机号：既是登录账号，也是唯一的业务身份标识 */
    private String phone;

    /**
     * BCrypt 密码散列。
     * ⚠️ 必须 @JsonIgnore：/api/user/me 直接返回本实体，不加注解会把散列下发给前端。
     */
    @JsonIgnore
    private String password;

    private String realName;
    private String nickname;
    private String avatar;
    /** 0未知 1男 2女 */
    private Integer gender;
    /** 0经营者 1前台管理员 2自习室用户 */
    private Integer role;
    /**
     * 所属门店。
     * <p>对前台管理员/经营者而言这是<b>数据访问范围</b>：其所有查询都必须带上本条件，
     * 否则前台就能看到别的门店的座位与营收。详见 REFERENCE-architecture.md 的权限要点。
     */
    private Long storeId;
    /** 1启用 0禁用 */
    private Integer status;
    /** 1接收站内消息 0不接收 */
    private Integer notifyEnabled;
    /** 1开启到期/预约提醒 0关闭 */
    private Integer remindEnabled;
    /** 累计学习时长（分钟，签退时结算累加） */
    private Integer totalStudyMinutes;
    private LocalDateTime lastLoginAt;

    /**
     * 密码最后修改时间。
     * <p>JWT 是无状态令牌、签发后无法撤销，改密后旧 token 依然有效。
     * AuthInterceptor 会拒绝签发时间早于本字段的 token，效果是「改密 = 踢下线所有旧设备」。
     */
    private LocalDateTime passwordChangedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
