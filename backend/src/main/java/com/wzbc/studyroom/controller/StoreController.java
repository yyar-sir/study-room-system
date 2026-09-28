package com.wzbc.studyroom.controller;

import com.wzbc.studyroom.common.Result;
import com.wzbc.studyroom.entity.Store;
import com.wzbc.studyroom.security.RequireRole;
import com.wzbc.studyroom.service.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 门店管理（仅自习室经营者，role = 0）。
 *
 * <p>类级 {@code @RequireRole(0)} 由 {@code RoleAspect} 的 {@code @within} 切点拦截。
 * 注意这只是<b>权限的第二层</b>：第一层是 AuthInterceptor 判断「有没有登录」。
 * 拿前台管理员或用户的 token 调本接口，应当返回 403 而不是 401 ——
 * 401 表示「不知道你是谁」，403 表示「知道你是谁但不许调」，前端据此决定
 * 是跳登录页还是提示无权限，两者不能混用。
 */
@RestController
@RequestMapping("/api/owner/stores")
@RequireRole(0)
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;

    /** 门店列表 */
    @GetMapping
    public Result<List<Store>> list() {
        return Result.ok(storeService.listAll());
    }

    /** 门店详情 */
    @GetMapping("/{id}")
    public Result<Store> detail(@PathVariable Long id) {
        return Result.ok(storeService.getById(id));
    }
}
