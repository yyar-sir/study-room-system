package com.wzbc.studyroom.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 注册入参（公开接口，注册后固定为自习室用户角色 role=2） */
@Data
public class RegisterDTO {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度 6-20 位")
    private String password;

    @NotBlank(message = "昵称不能为空")
    @Size(max = 16, message = "昵称最长 16 位")
    private String nickname;

    /** 真实姓名（选填，用于前台核验身份） */
    @Size(max = 16, message = "姓名最长 16 位")
    private String realName;

    // 注：注册接口刻意【不允许】指定 role 与 storeId。
    // 若开放这两个字段，任何人都能注册成经营者（越权提权），
    // 前台管理员账号只能由经营者在后台创建。
}
