package com.wzbc.studyroom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 大模型配置。
 *
 * <p>{@code apiKey} 只由后端读取，任何查询接口返回前必须脱敏（只回显后 4 位）。
 * 实际生效的 Key 优先取数据库本字段，为空时回退到 application-local.yml 的 {@code ai.api-key}
 * —— 前者便于管理端改，后者保证密钥不进仓库。
 */
@Data
@TableName("ai_config")
public class AiConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String provider;
    private String baseUrl;
    private String model;
    private String apiKey;
    private BigDecimal temperature;
    private Integer timeoutMs;
    /** 单用户每分钟请求上限 */
    private Integer rateLimitPerMin;
    /** 1开启内容安全过滤 */
    private Integer contentSafety;
    /** 1开启降级 */
    private Integer fallbackEnabled;
    private String systemPrompt;
    /** 1启用 0停用 */
    private Integer status;

    private LocalDateTime updatedAt;
}
