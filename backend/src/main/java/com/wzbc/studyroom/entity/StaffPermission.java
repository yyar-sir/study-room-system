package com.wzbc.studyroom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 前台管理员岗位权限。
 *
 * <p>经营者可以给不同前台分配不同操作权限，例如只让某人负责签到协助、
 * 不让其接触会员账目。权限键示例：
 * {@code seat:manage} {@code checkin:assist} {@code member:handle}
 * {@code record:query} {@code announcement:publish}
 */
@Data
@TableName("staff_permission")
public class StaffPermission {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String permKey;
    private Long grantedBy;

    private LocalDateTime createdAt;
}
