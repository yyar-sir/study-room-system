package com.wzbc.studyroom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志。
 *
 * <p>为什么要有这张表：此前「谁在什么时候改了什么」只存在于 logback 滚动日志里，
 * 文件会被覆盖、也没有按用户/模块检索的能力，真要追责无从回溯。
 * 任务书要求数据备份覆盖「系统操作日志」，指的也是这张表。
 *
 * <p>写入方式是 AOP 统一拦截（见 {@code security/OperationLogAspect}），
 * 业务代码零侵入——不需要每个 Service 自己写一行「记日志」。
 */
@Data
@TableName("operation_log")
public class OperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作人；匿名请求（如登录、注册）为 null */
    private Long userId;
    /** 冗余用户名：账号被删掉之后日志仍然可读 */
    private String username;

    private String module;
    /** 人类可读的操作描述，如「POST /api/staff/announcements」 */
    private String action;
    /** 请求方法与路径，用于精确检索 */
    private String method;
    /** 请求参数（已脱敏：口令类字段一律替换为 ***） */
    private String params;
    private String ip;
    /** 1 成功 0 抛异常 */
    private Integer success;
    private Integer costMs;

    private LocalDateTime createdAt;
}
