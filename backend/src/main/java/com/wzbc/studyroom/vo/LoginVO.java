package com.wzbc.studyroom.vo;

import lombok.Builder;
import lombok.Data;

/** 登录/注册返回体：token + 前端渲染所需的最小用户信息（绝不含密码散列） */
@Data
@Builder
public class LoginVO {
    private String token;
    private Long id;
    /** 手机号（登录账号） */
    private String phone;
    private String realName;
    private String nickname;
    private String avatar;
    /** 0经营者 1前台管理员 2自习室用户 */
    private Integer role;
    /** 数据访问范围门店（前台管理员/经营者） */
    private Long storeId;
}
