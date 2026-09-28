package com.wzbc.studyroom.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** JWT 配置项（application.yml 的 jwt.*） */
@Data
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    private String secret;
    private long expireHours = 72;
}
