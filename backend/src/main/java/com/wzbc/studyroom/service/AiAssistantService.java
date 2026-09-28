package com.wzbc.studyroom.service;

import com.wzbc.studyroom.dto.AiAskDTO;
import com.wzbc.studyroom.entity.AiChatRecord;
import com.wzbc.studyroom.entity.AiConfig;
import com.wzbc.studyroom.vo.AiAnswerVO;

import java.util.List;

/**
 * AI 智能自习助手。
 *
 * <p>核心设计原则（来自实战经验）：<b>把大模型当成一个随时会挂、会超时、
 * 会返回垃圾数据的不可靠外部依赖</b>。只做到「能调通」是作业水平；
 * 做到「挂了也不影响平台运行」才是工程水平。
 *
 * <p>因此本接口<b>永远不会抛出未处理异常</b>：任何失败都会落到
 * 知识库兜底答案或场景化友好文案，由 {@code degraded=true} 标记。
 */
public interface AiAssistantService {

    /** 提问（含限流、内容安全、知识库增强与三级降级） */
    AiAnswerVO ask(Long userId, AiAskDTO dto);

    /** 会话历史；sessionId 为空时返回该用户最近若干条 */
    List<AiChatRecord> history(Long userId, String sessionId);

    /** 供经营端查看的大模型配置（apiKey 已脱敏，只回显后 4 位） */
    AiConfig maskedConfig();
}
