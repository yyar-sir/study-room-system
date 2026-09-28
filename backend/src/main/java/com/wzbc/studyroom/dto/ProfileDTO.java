package com.wzbc.studyroom.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 个人资料维护入参。
 *
 * <p>刻意<b>不</b>包含 phone / role / status：手机号是登录账号，
 * 角色与状态属于权限范畴，都不允许用户自己改（否则就成了提权漏洞）。
 */
@Data
public class ProfileDTO {

    @Size(max = 16, message = "昵称最长 16 位")
    private String nickname;

    @Size(max = 16, message = "姓名最长 16 位")
    private String realName;

    /** 0未知 1男 2女 */
    private Integer gender;

    @Size(max = 255, message = "头像地址过长")
    private String avatar;
}
