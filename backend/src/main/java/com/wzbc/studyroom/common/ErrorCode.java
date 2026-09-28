package com.wzbc.studyroom.common;

/**
 * 全局错误码表。
 *
 * <p>为什么要集中定义：如果错误码散落在各个 Service 里写魔法数字，
 * 到后期必然出现「同一个码表示两种含义」或「同一种错误用了两个码」，
 * 前端也就没法按码做稳定分支。集中一处后，前端可以只认这张表。
 *
 * <p>分段约定：
 * <ul>
 *   <li>0       成功</li>
 *   <li>1xxx    通用 / 账号认证</li>
 *   <li>2xxx    预约与签到</li>
 *   <li>3xxx    门店与座位资源</li>
 *   <li>4xxx    会员套餐与计费</li>
 *   <li>5xxx    运营配置</li>
 *   <li>401/403 与 HTTP 状态码对齐，由拦截器与 AOP 直接返回</li>
 * </ul>
 */
public final class ErrorCode {

    private ErrorCode() {
    }

    // ---------- 通用 ----------
    /** 通用业务失败（兜底） */
    public static final int BIZ_ERROR = 1001;
    /** 参数校验失败 */
    public static final int PARAM_INVALID = 1002;

    // ---------- 账号与认证 ----------
    /** 账号或密码错误（刻意与「账号不存在」合并，防账号枚举） */
    public static final int LOGIN_FAILED = 1003;
    /** 账号已被禁用 */
    public static final int ACCOUNT_DISABLED = 1004;
    /** 手机号已注册 */
    public static final int PHONE_EXISTS = 1005;
    /** 原密码不正确 */
    public static final int OLD_PASSWORD_WRONG = 1006;
    /** 新密码与原密码相同 */
    public static final int PASSWORD_SAME = 1007;
    /** 密码错误次数过多，账号临时锁定 */
    public static final int LOGIN_LOCKED = 1008;

    // ---------- 预约与签到 ----------
    /** 座位不存在或当前不可预约 */
    public static final int SEAT_UNAVAILABLE = 2001;
    /** 该时段已被占用（预约时间冲突） */
    public static final int RESERVE_CONFLICT = 2002;
    /** 预约不存在或当前状态不允许该操作 */
    public static final int RESERVE_STATE_INVALID = 2003;
    /** 签到码无效 */
    public static final int CHECKIN_CODE_INVALID = 2004;
    /** 该预约已签到，请勿重复操作 */
    public static final int ALREADY_CHECKED_IN = 2005;
    /** 尚未到可签到时间 / 已超过允许签到的时间窗 */
    public static final int CHECKIN_TIME_INVALID = 2006;

    // ---------- 门店与座位 ----------
    /** 门店不存在或已停业 */
    public static final int STORE_UNAVAILABLE = 3001;

    // ---------- 会员与计费 ----------
    /** 套餐不存在或已下架 */
    public static final int PLAN_UNAVAILABLE = 4001;
    /** 没有可用的套餐权益 */
    public static final int NO_AVAILABLE_MEMBERSHIP = 4002;
    /** 超出套餐每日预约次数上限 */
    public static final int DAILY_LIMIT_EXCEEDED = 4003;
    /** 订单不存在或状态不允许该操作 */
    public static final int ORDER_STATE_INVALID = 4004;

    // ---------- 运营配置 ----------
    /** 配置项不存在 */
    public static final int CONFIG_NOT_FOUND = 5001;

    // ---------- AI 助手 ----------
    /** AI 服务暂时不可用（三级降级已生效，返回知识库兜底答案） */
    public static final int AI_DEGRADED = 6001;
    /** AI 请求过于频繁（触发限流） */
    public static final int AI_RATE_LIMITED = 6002;
    /** 提问内容未通过内容安全校验 */
    public static final int AI_CONTENT_REJECTED = 6003;

    // ---------- 与 HTTP 状态码对齐 ----------
    /** 未登录 */
    public static final int UNAUTHORIZED = 401;
    /** 已登录但无权限 */
    public static final int FORBIDDEN = 403;
}
