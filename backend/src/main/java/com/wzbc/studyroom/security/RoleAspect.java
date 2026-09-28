package com.wzbc.studyroom.security;

import com.wzbc.studyroom.common.BizException;
import com.wzbc.studyroom.common.ErrorCode;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/** 角色权限切面：校验 @RequireRole，未登录/角色不符 → 401/403
 *  （方法级注解与类级注解分开定义切点，避免 OR 表达式绑定问题） */
@Aspect
@Component
public class RoleAspect {

    @Before("@annotation(requireRole)")
    public void checkMethod(RequireRole requireRole) {
        check(requireRole);
    }

    @Before("@within(requireRole)")
    public void checkClass(RequireRole requireRole) {
        check(requireRole);
    }

    private void check(RequireRole requireRole) {
        LoginUser user = UserContext.get();
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        if (Arrays.stream(requireRole.value()).noneMatch(r -> r == user.getRole())) {
            // 403 与 401 必须区分：401 表示「你是谁不知道」，403 表示「知道你是谁但不许调」。
            // 混用会让前端无法决定是跳登录页还是提示无权限。
            throw new BizException(ErrorCode.FORBIDDEN, "无权限访问");
        }
    }
}
