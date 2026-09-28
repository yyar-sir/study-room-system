package com.wzbc.studyroom.service;

import com.wzbc.studyroom.entity.SystemConfig;

import java.util.List;

/**
 * 运营规则配置读取。
 *
 * <p>这些规则（提前预约天数、取消时限、签到时间窗、爽约判定）都是经营者可配的，
 * 因此<b>不能硬编码在 Service 里</b>。统一走本服务读取，并带回退默认值：
 * 配置缺失时用默认值而不是抛异常，避免因为少配一条规则导致整个预约功能不可用。
 */
public interface SystemConfigService {

    /** 取字符串配置：先找门店级，找不到再找全局（storeId=0），都没有则返回默认值 */
    String get(Long storeId, String key, String defaultValue);

    /** 取整型配置，解析失败或缺失时返回默认值 */
    int getInt(Long storeId, String key, int defaultValue);

    /** 列出配置项（供经营端「运营规则配置」页展示） */
    List<SystemConfig> list(Long storeId, String group);

    /**
     * 新增或更新一条配置（按 storeId + configKey 唯一）。
     * <p>用 upsert 而不是让前端区分「新增/编辑」：经营者改一条规则时
     * 不该关心它此前是否存在。
     */
    void save(Long storeId, String configKey, String configValue, String configGroup, String description);

    // ---- 配置键常量，避免各处写错字符串 ----
    String ADVANCE_BOOK_DAYS = "advance_book_days";
    String CANCEL_BEFORE_MINUTES = "cancel_before_minutes";
    String OCCUPY_TIMEOUT_MINUTES = "occupy_timeout_minutes";
    String CHECKIN_EARLY_MINUTES = "checkin_early_minutes";
    String CHECKIN_LATE_MINUTES = "checkin_late_minutes";
    String PLAN_EXPIRE_DAYS = "plan_expire_days";
    /**
     * 预约开始前多少分钟发送提醒。
     *
     * <p>这条配置此前只存在于初始化 SQL 里，代码从未读取，导致
     * 任务书要求的「预约即将开始的提醒」实际没有实现——配置项躺在库里
     * 却没有消费者，是比没有配置更隐蔽的缺失。
     */
    String RESERVE_REMIND_MINUTES = "reserve_remind_minutes";

    /**
     * AI 对话记录保留天数（默认 30）。
     *
     * <p>ai_chat_record 只增不减：每次提问都会写两条（用户 + 助手），
     * 演示环境一天问几十次就会持续堆积，且这张表对业务没有任何读路径
     * 之外的用途。配成 0 或负数表示不清理。
     */
    String AI_CHAT_RETAIN_DAYS = "ai_chat_retain_days";

    /**
     * 操作日志保留天数（默认 90）。
     *
     * <p>操作日志由 AOP 记录每一次写操作，同样只增不减。这里保留得比
     * 对话记录久，因为它是审计依据——出了事要能回溯至少一个季度。
     */
    String OPERATION_LOG_RETAIN_DAYS = "operation_log_retain_days";
    /**
     * 单次预约最长时长（小时）。
     *
     * <p>没有这一条会出大问题：营业时间是 08:00-22:00，若只校验"在营业时间内"，
     * 用户可以一次预约 08:00-22:00（14 小时）把整天的座位锁死，别人全天约不到。
     * 这是实际运营中必须有的约束。
     */
    String MAX_RESERVE_HOURS = "max_reserve_hours";
}
