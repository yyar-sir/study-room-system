package com.wzbc.studyroom.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** AI 助手提问入参 */
@Data
public class AiAskDTO {

    @NotBlank(message = "请输入你想问的问题")
    @Size(max = 500, message = "问题最长 500 字")
    private String question;

    /** 会话 id，前端生成；同一会话的多轮对话归为一组，便于查看历史 */
    @Size(max = 64, message = "会话标识过长")
    private String sessionId;
}
