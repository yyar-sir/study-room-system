package com.wzbc.studyroom.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wzbc.studyroom.common.BizException;
import com.wzbc.studyroom.common.ErrorCode;
import com.wzbc.studyroom.common.Result;
import com.wzbc.studyroom.entity.StaffPermission;
import com.wzbc.studyroom.entity.User;
import com.wzbc.studyroom.mapper.StaffPermissionMapper;
import com.wzbc.studyroom.mapper.StoreMapper;
import com.wzbc.studyroom.mapper.UserMapper;
import com.wzbc.studyroom.security.RequireRole;
import com.wzbc.studyroom.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 人员权限管理（仅经营者，role = 0）：管理前台管理员账号、分配岗位权限与数据访问范围。
 *
 * <p>设计要点：
 * <ul>
 *   <li>前台账号<b>只能由经营者创建</b>。注册接口固定 role=2，
 *       否则任何人都能注册成前台（越权提权）。</li>
 *   <li>门店（storeId）就是前台的数据访问范围，Service 层所有门店相关查询都据此过滤。</li>
 *   <li>权限是「覆盖式」设置而不是增量增删 —— 前端提交完整权限集合，
 *       服务端先清空再写入，避免出现"取消了但没删掉"的残留。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/owner/staff")
@RequireRole(0)
@RequiredArgsConstructor
public class OwnerStaffController {

    private final UserMapper userMapper;
    private final StoreMapper storeMapper;
    private final StaffPermissionMapper permissionMapper;

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    /** 可选权限键（前端据此渲染勾选项） */
    private static final List<Map<String, String>> PERMISSION_CATALOG = List.of(
            perm("seat:manage", "座位状态管理"),
            perm("checkin:assist", "签到签退协助"),
            perm("member:handle", "会员业务办理"),
            perm("record:query", "记录查询"),
            perm("announcement:publish", "公告发布")
    );

    private static Map<String, String> perm(String key, String label) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("label", label);
        return m;
    }

    /** 可选权限目录 */
    @GetMapping("/permission-catalog")
    public Result<List<Map<String, String>>> catalog() {
        return Result.ok(PERMISSION_CATALOG);
    }

    /** 前台账号列表（含所属门店与已有权限） */
    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        List<User> staff = userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getRole, 1)
                .orderByDesc(User::getCreatedAt));
        List<Map<String, Object>> out = new ArrayList<>();
        for (User u : staff) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("phone", u.getPhone());
            m.put("realName", u.getRealName());
            m.put("nickname", u.getNickname());
            m.put("storeId", u.getStoreId());
            m.put("status", u.getStatus());
            m.put("lastLoginAt", u.getLastLoginAt());
            m.put("createdAt", u.getCreatedAt());
            m.put("permissions", permissionsOf(u.getId()));
            out.add(m);
        }
        return Result.ok(out);
    }

    /** 创建前台账号 */
    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        String phone = str(body.get("phone"));
        String password = str(body.get("password"));

        if (phone == null || !phone.matches("^1[3-9]\\d{9}$")) {
            throw new BizException(ErrorCode.PARAM_INVALID, "手机号格式不正确");
        }
        if (password == null || password.length() < 6) {
            throw new BizException(ErrorCode.PARAM_INVALID, "密码至少 6 位");
        }
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.PHONE_EXISTS, "该手机号已被注册");
        }
        Long storeId = longOf(body.get("storeId"));
        if (storeId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请为前台指定所属门店（数据访问范围）");
        }
        if (storeMapper.selectById(storeId) == null) {
            throw new BizException(ErrorCode.STORE_UNAVAILABLE, "门店不存在");
        }

        User u = new User();
        u.setPhone(phone);
        u.setPassword(ENCODER.encode(password));   // 与用户端同一套 BCrypt，绝不明文
        u.setRealName(str(body.get("realName")));
        u.setNickname(str(body.get("nickname")) == null ? "前台" : str(body.get("nickname")));
        u.setGender(0);
        u.setRole(1);                              // 固定前台，不接受前端传入
        u.setStoreId(storeId);
        u.setStatus(1);
        u.setNotifyEnabled(1);
        u.setRemindEnabled(1);
        u.setTotalStudyMinutes(0);
        userMapper.insert(u);

        savePermissions(u.getId(), stringList(body.get("permissions")), UserContext.userId());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("phone", u.getPhone());
        return Result.ok(m);
    }

    /** 设置权限（覆盖式） */
    @PostMapping("/{id}/permissions")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> setPermissions(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        requireStaff(id);
        savePermissions(id, stringList(body.get("permissions")), UserContext.userId());
        return Result.ok();
    }

    /** 启用 / 停用前台账号。停用后其 token 立即失效（AuthInterceptor 会校验账号状态）。 */
    @PostMapping("/{id}/status")
    public Result<Void> setStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        User staff = requireStaff(id);
        Object s = body.get("status");
        if (s == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "缺少 status 参数");
        }
        int status = Integer.parseInt(s.toString());
        if (status != 0 && status != 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 只能是 1启用 / 0禁用");
        }
        User patch = new User();
        patch.setId(staff.getId());
        patch.setStatus(status);
        userMapper.updateById(patch);
        return Result.ok();
    }

    /** 调整前台所属门店（数据访问范围） */
    @PostMapping("/{id}/store")
    public Result<Void> setStore(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        User staff = requireStaff(id);
        Long storeId = longOf(body.get("storeId"));
        if (storeId == null || storeMapper.selectById(storeId) == null) {
            throw new BizException(ErrorCode.STORE_UNAVAILABLE, "门店不存在");
        }
        User patch = new User();
        patch.setId(staff.getId());
        patch.setStoreId(storeId);
        userMapper.updateById(patch);
        return Result.ok();
    }

    // ------------------------------------------------------------------

    private User requireStaff(Long id) {
        User u = userMapper.selectById(id);
        if (u == null || u.getRole() == null || u.getRole() != 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, "前台账号不存在");
        }
        return u;
    }

    private List<String> permissionsOf(Long userId) {
        List<StaffPermission> list = permissionMapper.selectList(new LambdaQueryWrapper<StaffPermission>()
                .eq(StaffPermission::getUserId, userId));
        List<String> out = new ArrayList<>();
        for (StaffPermission p : list) {
            out.add(p.getPermKey());
        }
        return out;
    }

    private void savePermissions(Long userId, List<String> perms, Long grantedBy) {
        // 覆盖式：先清空再写入，避免出现"界面取消了但库里还在"的残留权限
        permissionMapper.delete(new LambdaQueryWrapper<StaffPermission>()
                .eq(StaffPermission::getUserId, userId));
        if (perms == null || perms.isEmpty()) {
            return;
        }
        for (String key : perms) {
            if (key == null || key.isBlank()) {
                continue;
            }
            StaffPermission p = new StaffPermission();
            p.setUserId(userId);
            p.setPermKey(key.trim());
            p.setGrantedBy(grantedBy);
            permissionMapper.insert(p);
        }
    }

    private String str(Object o) {
        return o == null ? null : o.toString();
    }

    private Long longOf(Object o) {
        if (o == null || "".equals(o)) {
            return null;
        }
        try {
            return Long.valueOf(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> stringList(Object o) {
        if (o instanceof List<?> l) {
            List<String> out = new ArrayList<>();
            for (Object x : l) {
                if (x != null) {
                    out.add(x.toString());
                }
            }
            return out;
        }
        return List.of();
    }
}
