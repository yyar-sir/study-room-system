package com.wzbc.studyroom.controller;

import com.wzbc.studyroom.common.Result;
import com.wzbc.studyroom.dto.AiAskDTO;
import com.wzbc.studyroom.entity.AiChatRecord;
import com.wzbc.studyroom.entity.AiConfig;
import com.wzbc.studyroom.security.RequireRole;
import com.wzbc.studyroom.security.UserContext;
import com.wzbc.studyroom.service.AiAssistantService;
import com.wzbc.studyroom.vo.AiAnswerVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 智能自习助手接口。
 *
 * <p>提问接口只对自习室用户开放（role 2）；大模型配置查看只对经营者开放（role 0），
 * 且返回的 apiKey 已脱敏。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AiController {

    private final AiAssistantService aiAssistantService;

    /**
     * 提问。
     *
     * <p>注意：本接口内部已做限流与降级，失败时返回的是<b>友好文案</b>而不是错误码，
     * 只有「问得太快」会返回业务码 6002。前端应把超时设长一些（生成类请求建议 30s 起）。
     */
    @PostMapping("/user/ai/ask")
    @RequireRole(2)
    public Result<AiAnswerVO> ask(@Valid @RequestBody AiAskDTO dto) {
        return Result.ok(aiAssistantService.ask(UserContext.userId(), dto));
    }

    /** 会话历史 */
    @GetMapping("/user/ai/history")
    @RequireRole(2)
    public Result<List<AiChatRecord>> history(@RequestParam(required = false) String sessionId) {
        return Result.ok(aiAssistantService.history(UserContext.userId(), sessionId));
    }

    /** 大模型配置（经营端；apiKey 脱敏，只回显后 4 位） */
    @GetMapping("/owner/ai/config")
    @RequireRole(0)
    public Result<AiConfig> config() {
        return Result.ok(aiAssistantService.maskedConfig());
    }
}
