package com.wzbc.studyroom.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** AI 助手回答（接口层永远返回这个结构，绝不把异常堆栈抛给前端） */
@Data
@Builder
public class AiAnswerVO {

    private String sessionId;
    private String answer;

    /**
     * 是否为降级响应。
     * <p>前端据此展示一个「AI 暂不可用，以下为知识库答案」的小标记，
     * 而不是让用户以为模型给出的就是标准答案。
     */
    private Boolean degraded;

    /** 降级原因，便于前端/运维区分是超时、未配 Key 还是内容被拦 */
    private String degradeReason;

    /** 识别出的意图：plan_query / seat_query / rule_query / booking / study_plan / other */
    private String intent;

    /** 本次回答命中的知识库条目 id（可为空） */
    private List<Long> matchedKnowledgeIds;

    private Integer tokens;
    private Integer costMs;
}
