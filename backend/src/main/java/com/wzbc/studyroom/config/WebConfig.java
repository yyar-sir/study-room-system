package com.wzbc.studyroom.config;

import com.wzbc.studyroom.security.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

/** Web 配置：跨域 + JWT 拦截器 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    /**
     * 额外允许的跨域来源（逗号分隔），默认空。
     *
     * <p>默认已放行本地开发的 localhost / 127.0.0.1 任意端口（Vite 端口可能变动）。
     * 浏览器对同源 POST 也会带上 Origin 头，因此部署到公网后<b>必须</b>把真实域名
     * 加进来，否则登录等 POST 接口会被 Spring 以 {@code 403 Invalid CORS request} 拒绝。
     *
     * <p>生产环境在 {@code application-prod.yml} 或环境变量中配置，例如：
     * <pre>
     * cors:
     *   allowed-origins: "https://your-domain.com,https://www.your-domain.com"
     * </pre>
     */
    @Value("${cors.allowed-origins:}")
    private String extraAllowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> patterns = new ArrayList<>(List.of("http://localhost:*", "http://127.0.0.1:*"));
        if (extraAllowedOrigins != null && !extraAllowedOrigins.isBlank()) {
            for (String origin : extraAllowedOrigins.split(",")) {
                String trimmed = origin.trim();
                if (!trimmed.isEmpty()) {
                    patterns.add(trimmed);
                }
            }
        }
        registry.addMapping("/api/**")
                .allowedOriginPatterns(patterns.toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**")
                .excludePathPatterns("/api/public/**");
    }
}
