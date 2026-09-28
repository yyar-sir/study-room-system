package com.wzbc.studyroom.security;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录用户上下文对象（当前请求的「我是谁」）。
 *
 * <p>{@code storeId} 是前台管理员/经营者的<b>数据访问范围</b>。
 * 它刻意<b>不从 JWT 里取</b>，而是由 AuthInterceptor 每次从数据库读最新的 user.store_id。
 * 原因：门店调动属于权限变更，应当立即生效；若把 storeId 写进 token，
 * 调动后旧 token 仍携带旧门店，会出现「已调岗但还能看原门店数据」的越权窗口。
 */
@Data
@AllArgsConstructor
public class LoginUser {
    private Long id;
    private String phone;
    /** 0经营者 1前台管理员 2自习室用户 */
    private Integer role;
    /** 数据访问范围（门店 id），经营者可为 null 表示全部门店 */
    private Long storeId;
}
