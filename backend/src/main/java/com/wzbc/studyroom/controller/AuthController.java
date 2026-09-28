package com.wzbc.studyroom.controller;

import com.wzbc.studyroom.common.Result;
import com.wzbc.studyroom.dto.ChangePasswordDTO;
import com.wzbc.studyroom.dto.LoginDTO;
import com.wzbc.studyroom.dto.ProfileDTO;
import com.wzbc.studyroom.dto.RegisterDTO;
import com.wzbc.studyroom.dto.ResetPasswordDTO;
import com.wzbc.studyroom.entity.User;
import com.wzbc.studyroom.security.UserContext;
import com.wzbc.studyroom.service.UserService;
import com.wzbc.studyroom.vo.LoginVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认证接口。
 *
 * <p>⚠️ 本 Controller 下的 /api/auth/** 必须在 WebConfig 的 excludePathPatterns 白名单里，
 * 否则未登录时调登录接口本身就会被拦截器拦掉（死循环）。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    /** 登录（公开） */
    @PostMapping("/auth/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.ok(userService.login(dto));
    }

    /** 注册（公开，固定注册为自习室用户） */
    @PostMapping("/auth/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterDTO dto) {
        return Result.ok(userService.register(dto));
    }

    /** 当前登录用户信息（需登录；User.password 上有 @JsonIgnore，散列不会下发） */
    @GetMapping("/user/me")
    public Result<User> me() {
        return Result.ok(userService.getById(UserContext.userId()));
    }

    /** 修改密码（需登录）。成功后旧 token 全部失效，前端应引导重新登录。 */
    @PostMapping("/user/change-password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        userService.changePassword(UserContext.userId(), dto);
        return Result.ok();
    }

    /**
     * 维护个人资料（需登录）。
     * <p>注意入参里没有 phone / role / status —— 手机号是登录账号、角色与状态属于权限，
     * 都不允许用户自己改，否则就是提权漏洞。
     */
    @PutMapping("/user/profile")
    public Result<User> updateProfile(@Valid @RequestBody ProfileDTO dto) {
        return Result.ok(userService.updateProfile(UserContext.userId(), dto));
    }

    /**
     * 忘记密码·获取验证码（公开）。
     *
     * <p>⚠️ 演示用：验证码直接在响应里返回。生产环境必须改为短信通道下发、
     * 绝不出现在响应体中，否则任何人都能重置别人的密码。
     */
    @PostMapping("/auth/forgot-password/send-code")
    public Result<Map<String, Object>> sendResetCode(@RequestBody Map<String, String> body) {
        String phone = body == null ? null : body.get("phone");
        String code = userService.sendResetCode(phone);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("phone", phone);
        data.put("code", code);
        data.put("demoNotice", "演示环境直接返回验证码；生产环境应通过短信下发且不回传");
        return Result.ok(data);
    }

    /** 忘记密码·用验证码重置密码（公开） */
    @PostMapping("/auth/forgot-password/reset")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordDTO dto) {
        userService.resetPassword(dto);
        return Result.ok();
    }
}
