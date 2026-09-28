package com.wzbc.studyroom.security;

import com.wzbc.studyroom.entity.User;
import com.wzbc.studyroom.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.ZoneId;
import java.util.Date;

/**
 * JWT 认证拦截器：解析 Bearer token → 校验账号状态 → 写入 UserContext。
 *
 * <p><b>职责边界</b>：本拦截器只管「有没有登录」，<b>不管</b>「这个角色能不能调」。
 * 角色校验由 {@link RequireRole} + {@link RoleAspect} 负责。
 * 两件事塞进一处会让白名单越来越难维护，也会让「已登录但无权限」只能返回 401 而不是 403。
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            Claims claims = jwtUtil.parse(auth.substring(7));
            if (claims != null) {
                LoginUser loginUser = resolve(claims);
                if (loginUser != null) {
                    UserContext.set(loginUser);
                    return true;
                }
            }
        }
        // 未登录 / 登录态已失效（非白名单接口）→ 401
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        try {
            response.getWriter().write("{\"code\":401,\"msg\":\"请先登录\",\"data\":null}");
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * token 是否仍然可用。除签名与有效期（{@code parse} 已校验）外还需满足：
     * <ol>
     *   <li>账号仍然存在且未被禁用 —— 否则已删除账号手里的 token 会一直有效；</li>
     *   <li>密码未在该 token 签发之后被修改过 —— 改密即等价于「下线所有旧设备」。</li>
     * </ol>
     *
     * <p>时间比较刻意用「严格早于」：JWT 的 {@code iat} 只精确到秒，
     * 若写成 {@code <=}，用户在改密后同一秒内重新登录拿到的新 token 也会被判为失效。
     *
     * <p>顺带在这里装配 {@code storeId}：它是数据访问范围，必须取数据库的最新值，
     * 不能信任 token 里的旧快照（否则调岗后存在越权窗口）。
     */
    private LoginUser resolve(Claims claims) {
        Long uid;
        try {
            uid = Long.valueOf(claims.getSubject());
        } catch (NumberFormatException e) {
            return null;
        }
        User user = userMapper.selectById(uid);
        if (user == null || (user.getStatus() != null && user.getStatus() == 0)) {
            return null;
        }
        Date issuedAt = claims.getIssuedAt();
        if (user.getPasswordChangedAt() != null && issuedAt != null) {
            long iat = issuedAt.toInstant().getEpochSecond();
            long pwdChangedAt = user.getPasswordChangedAt()
                    .atZone(ZoneId.systemDefault()).toInstant().getEpochSecond();
            if (iat < pwdChangedAt) {
                return null;
            }
        }
        // 角色以数据库为准，理由同 storeId：角色变更必须立即生效
        return new LoginUser(user.getId(), user.getPhone(), user.getRole(), user.getStoreId());
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
