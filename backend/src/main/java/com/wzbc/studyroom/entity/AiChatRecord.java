package com.wzbc.studyroom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** AI 对话记录 */
@Data
@TableName("ai_chat_record")
public class AiChatRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String sessionId;
    /** user / assistant */
    private String role;
    private String content;
    /** 识别出的意图 */
    private String intent;
    /** 1 表示本次是降级响应（大模型不可用，返回知识库或兜底文案） */
    private Integer degraded;
    private Integer tokens;
    private Integer costMs;

    private LocalDateTime createdAt;
}
