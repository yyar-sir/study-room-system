package com.wzbc.studyroom.security;

import com.wzbc.studyroom.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具：签发 / 解析。
 *
 * <p>⚠️ token 里刻意<b>不放 storeId</b>：门店调动属于权限变更，必须立即生效，
 * 写进 token 会让旧 token 一直带着旧门店。数据访问范围统一由 AuthInterceptor
 * 在每次请求时从数据库读取（见 {@link LoginUser} 的说明）。
 *
 * <p>密钥长度：jjwt 0.12.x 严格执行 RFC 7518，HS256 要求密钥 ≥ 256 bit。
 * 若配置的 secret 短于 32 个字符，签发时会抛 {@code WeakKeyException}，
 * 表现为「登录接口 500」而不报任何配置错误 —— 这是最容易查错方向的坑。
 */
@Component
@RequiredArgsConstructor
public class JwtUtil {

    private final JwtProperties props;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /** 签发 token：subject=userId，claims 携带 phone/role */
    public String createToken(Long userId, String phone, Integer role) {
        long expireMs = props.getExpireHours() * 3600_000L;
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("phone", phone)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expireMs))
                .signWith(key())
                .compact();
    }

    /** 解析 token，失败返回 null（不会抛异常） */
    public Claims parse(String token) {
        try {
            return Jwts.parser().verifyWith(key()).build()
                    .parseSignedClaims(token).getPayload();
        } catch (Exception e) {
            return null;
        }
    }
}
