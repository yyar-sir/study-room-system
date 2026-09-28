package com.wzbc.studyroom.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wzbc.studyroom.common.BizException;
import com.wzbc.studyroom.common.ErrorCode;
import com.wzbc.studyroom.dto.AiAskDTO;
import com.wzbc.studyroom.entity.AiChatRecord;
import com.wzbc.studyroom.entity.AiConfig;
import com.wzbc.studyroom.entity.AiKnowledge;
import com.wzbc.studyroom.mapper.AiChatRecordMapper;
import com.wzbc.studyroom.mapper.AiConfigMapper;
import com.wzbc.studyroom.mapper.AiKnowledgeMapper;
import com.wzbc.studyroom.service.AiAssistantService;
import com.wzbc.studyroom.vo.AiAnswerVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AI 智能自习助手实现。
 *
 * <p><b>三级降级</b>（这是本模块的核心，答辩重点）：
 * <ol>
 *   <li><b>一级 —— 正常</b>：调用大模型，返回模型答案；</li>
 *   <li><b>二级 —— 知识库兜底</b>：网络异常/超时/非 200 时，改用自习室知识库里
 *       命中的标准答案，功能不中断（{@code degraded=true}，reason 记录具体原因）；</li>
 *   <li><b>三级 —— 场景化文案</b>：连知识库也没命中时，返回按场景区分的友好提示，
 *       <b>绝不抛异常、绝不白屏</b>。</li>
 * </ol>
 * 另外「未配置 API Key」单独走可配置提示，便于管理员定位问题而不是看到"网络异常"。
 *
 * <p>HTTP 客户端使用 JDK 内置 {@code java.net.http.HttpClient}，
 * 不引任何 SDK —— 换模型只需改 {@code ai_config} 表，不用改代码。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAssistantServiceImpl implements AiAssistantService {

    private final AiConfigMapper configMapper;
    private final AiKnowledgeMapper knowledgeMapper;
    private final AiChatRecordMapper chatMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 本地配置里的 Key（application-local.yml，已被 gitignore）。数据库里有值时优先用数据库的。 */
    @Value("${ai.api-key:}")
    private String configuredApiKey;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    // ------------------------------------------------------------------
    //  限流：按 userId 的每分钟固定窗口
    //  说明：进程内实现，单机部署足够；多实例部署必须换 Redis，
    //  否则限流额度会随实例数翻倍。
    // ------------------------------------------------------------------
    private static final Map<Long, RateLimiter> LIMITERS = new ConcurrentHashMap<>();

    private static final class RateLimiter {
        private final int limit;
        private final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());
        private final AtomicInteger count = new AtomicInteger();

        RateLimiter(int limit) {
            this.limit = Math.max(limit, 1);
        }

        synchronized boolean tryAcquire() {
            long now = System.currentTimeMillis();
            if (now - windowStart.get() >= 60_000L) {
                windowStart.set(now);
                count.set(0);
            }
            return count.incrementAndGet() <= limit;
        }
    }

    // ------------------------------------------------------------------
    //  场景化兜底文案：刻意按场景区分，不要所有场景共用一句「服务繁忙」，
    //  否则用户无法判断是自己网络问题还是平台问题。
    // ------------------------------------------------------------------
    private static final String FALLBACK_GENERIC =
            "AI 助手暂时开小差了，请稍后再试；你也可以在「会员套餐」页查看套餐详情，或直接联系前台。";
    private static final String FALLBACK_NO_KEY =
            "AI 助手尚未完成配置，请联系门店管理员在后台填写大模型 API Key。";
    private static final String FALLBACK_UNSAFE =
            "这个问题我不太方便回答呢，我们聊点自习室相关的吧——比如套餐、选座、预约规则～";

    /** 内容安全：命中后【替换】为安全文案，而不是抛错（用户体验优先） */
    private static final String[] SENSITIVE = {
            "色情", "赌博", "毒品", "枪支", "暴力恐怖", "法轮", "翻墙", "代考", "代写论文", "作弊"
    };

    /**
     * 意图 → 优先召回的知识库分类。
     * ai_knowledge.category 的取值就是「套餐 / 座位 / 规则 / 门店 / 其它」，
     * 与意图天然对应，因此不需要额外维护映射表。
     */
    private static final Map<String, String> INTENT_CATEGORY = Map.of(
            "plan_query", "套餐",
            "seat_query", "座位",
            "rule_query", "规则");

    /** 意图 → 追加给模型的行为约束，让「识别出意图」真正改变回答方式 */
    private static final Map<String, String> INTENT_HINT = Map.of(
            "plan_query", "用户在问套餐。请明确价格、包含次数与有效期，并做性价比对比。",
            "seat_query", "用户在问座位。请结合本店座位类型说明差异与适用场景，必要时建议先看实时座位图。",
            "rule_query", "用户在问预约规则。请准确引用规则数字（最多提前天数、单次时长上限、取消时限、签到时间窗）。",
            "study_plan", "用户想聊学习安排。请给出可执行的建议（时段选择、单次时长、休息节奏、目标拆解），"
                    + "不要只复述门店规则。");

    @Override
    public AiAnswerVO ask(Long userId, AiAskDTO dto) {
        long t0 = System.currentTimeMillis();
        String question = dto.getQuestion() == null ? "" : dto.getQuestion().trim();
        String sessionId = (dto.getSessionId() == null || dto.getSessionId().isBlank())
                ? UUID.randomUUID().toString().replace("-", "")
                : dto.getSessionId();

        AiConfig cfg = loadConfig();
        if (cfg == null || cfg.getStatus() == null || cfg.getStatus() != 1) {
            return persistAndReturn(userId, sessionId, question, FALLBACK_GENERIC,
                    true, "AI 功能未启用", "other", List.of(), 0, t0);
        }

        // ① 限流（超限抛业务异常，由全局异常处理器转成友好提示）
        int limit = cfg.getRateLimitPerMin() == null ? 10 : cfg.getRateLimitPerMin();
        RateLimiter limiter = LIMITERS.computeIfAbsent(userId, k -> new RateLimiter(limit));
        if (!limiter.tryAcquire()) {
            throw new BizException(ErrorCode.AI_RATE_LIMITED,
                    "问得有点快啦，请稍等一会儿再问（每分钟最多 " + limit + " 次）");
        }

        // ② 内容安全（入口）
        String intent = detectIntent(question);
        if (isSafetyOn(cfg) && hitSensitive(question)) {
            log.warn("AI 提问命中内容安全过滤 userId={}", userId);
            return persistAndReturn(userId, sessionId, question, FALLBACK_UNSAFE,
                    false, null, "blocked", List.of(), 0, t0);
        }

        // ③ 知识库召回（关键词打分 + 意图加权），既用于增强提示词，也用于降级兜底
        List<AiKnowledge> hits = retrieve(question, 3, intent);
        List<Long> hitIds = new ArrayList<>();
        for (AiKnowledge k : hits) {
            hitIds.add(k.getId());
        }

        // ④ 调用大模型；任何失败都在这里被吃掉，转成降级答案
        String answer;
        boolean degraded = false;
        String degradeReason = null;
        int tokens = 0;
        String apiKey = resolveApiKey(cfg);
        if (apiKey == null || apiKey.isBlank()) {
            degraded = true;
            degradeReason = "未配置 API Key";
            answer = hits.isEmpty() ? FALLBACK_NO_KEY : knowledgeFallback(hits, "AI 尚未配置");
        } else {
            try {
                LlmResult r = callLlm(cfg, apiKey, buildSystemPrompt(cfg, hits, intent), question);
                answer = r.content;
                tokens = r.tokens;
                // ⑤ 内容安全（出口）：命中则替换，不报错
                if (isSafetyOn(cfg) && hitSensitive(answer)) {
                    answer = FALLBACK_UNSAFE;
                }
            } catch (Exception e) {
                // 一级降级：知识库兜底；知识库也没命中 → 场景化文案
                degraded = true;
                degradeReason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                log.warn("AI 调用失败，进入降级：{}", degradeReason);
                answer = hits.isEmpty() ? FALLBACK_GENERIC : knowledgeFallback(hits, "AI 暂时不可用");
            }
        }

        // ⑥ 命中计数（best-effort，失败不影响主流程）
        for (AiKnowledge k : hits) {
            try {
                AiKnowledge patch = new AiKnowledge();
                patch.setId(k.getId());
                patch.setHitCount((k.getHitCount() == null ? 0 : k.getHitCount()) + 1);
                knowledgeMapper.updateById(patch);
            } catch (Exception ignored) {
                // 统计失败不应影响用户拿到答案
            }
        }

        return persistAndReturn(userId, sessionId, question, answer, degraded, degradeReason,
                intent, hitIds, tokens, t0);
    }

    @Override
    public List<AiChatRecord> history(Long userId, String sessionId) {
        LambdaQueryWrapper<AiChatRecord> w = new LambdaQueryWrapper<AiChatRecord>()
                .eq(AiChatRecord::getUserId, userId)
                .orderByAsc(AiChatRecord::getCreatedAt)
                .last("LIMIT 100");
        if (sessionId != null && !sessionId.isBlank()) {
            w.eq(AiChatRecord::getSessionId, sessionId);
        }
        return chatMapper.selectList(w);
    }

    @Override
    public AiConfig maskedConfig() {
        AiConfig cfg = loadConfig();
        if (cfg == null) {
            return null;
        }
        AiConfig copy = new AiConfig();
        copy.setId(cfg.getId());
        copy.setProvider(cfg.getProvider());
        copy.setBaseUrl(cfg.getBaseUrl());
        copy.setModel(cfg.getModel());
        copy.setTemperature(cfg.getTemperature());
        copy.setTimeoutMs(cfg.getTimeoutMs());
        copy.setRateLimitPerMin(cfg.getRateLimitPerMin());
        copy.setContentSafety(cfg.getContentSafety());
        copy.setFallbackEnabled(cfg.getFallbackEnabled());
        copy.setSystemPrompt(cfg.getSystemPrompt());
        copy.setStatus(cfg.getStatus());
        // 密钥只回显后 4 位：管理端看不到完整 Key，也就无法泄露它
        copy.setApiKey(mask(resolveApiKey(cfg)));
        return copy;
    }

    // ==================================================================

    private AiConfig loadConfig() {
        List<AiConfig> list = configMapper.selectList(new LambdaQueryWrapper<AiConfig>()
                .orderByAsc(AiConfig::getId).last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    /** 数据库里有 Key 就用数据库的（管理端可改），否则回退到本地配置（不进仓库） */
    private String resolveApiKey(AiConfig cfg) {
        if (cfg.getApiKey() != null && !cfg.getApiKey().isBlank()) {
            return cfg.getApiKey();
        }
        return configuredApiKey;
    }

    private static String mask(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return "";
        }
        if (apiKey.length() <= 4) {
            return "****";
        }
        return "****" + apiKey.substring(apiKey.length() - 4);
    }

    private boolean isSafetyOn(AiConfig cfg) {
        return cfg.getContentSafety() != null && cfg.getContentSafety() == 1;
    }

    private boolean hitSensitive(String text) {
        if (text == null) {
            return false;
        }
        String t = text.toLowerCase();
        for (String w : SENSITIVE) {
            if (t.contains(w)) {
                return true;
            }
        }
        return false;
    }

    /** 关键词召回：命中越多排越前，并按意图对同类知识加权。简单但可解释，适合门店规模的知识库。 */
    private List<AiKnowledge> retrieve(String question, int topN, String intent) {
        if (question == null || question.isBlank()) {
            return List.of();
        }
        List<AiKnowledge> all = knowledgeMapper.selectList(new LambdaQueryWrapper<AiKnowledge>()
                .eq(AiKnowledge::getStatus, 1));
        String q = question.toLowerCase();
        String preferred = INTENT_CATEGORY.get(intent);
        Map<AiKnowledge, Integer> score = new LinkedHashMap<>();
        for (AiKnowledge k : all) {
            int s = 0;
            if (k.getKeywords() != null) {
                for (String kw : k.getKeywords().split("[,，]")) {
                    String w = kw.trim().toLowerCase();
                    if (!w.isEmpty() && q.contains(w)) {
                        s += 2;
                    }
                }
            }
            if (k.getQuestion() != null && !k.getQuestion().isBlank() && q.contains(k.getQuestion().toLowerCase())) {
                s += 3;
            }
            // 意图加权：识别出意图却不影响召回，那意图就只是个摆设。
            // ai_knowledge.category 的取值（套餐/座位/规则/门店）与意图天然对应。
            if (preferred != null && preferred.equals(k.getCategory())) {
                s += 4;
            }
            if (s > 0) {
                score.put(k, s);
            }
        }
        return score.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(topN)
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * 按意图追加行为约束。
     *
     * <p>没有这一步时，无论用户问什么，模型拿到的提示词都是同一份，
     * 「意图识别」就只体现在返回体里那个字符串上。
     */
    private String buildSystemPrompt(AiConfig cfg, List<AiKnowledge> hits, String intent) {
        StringBuilder sb = new StringBuilder();
        sb.append(cfg.getSystemPrompt() == null || cfg.getSystemPrompt().isBlank()
                ? "你是共享自习室的智能助手，只回答与自习室相关的问题。"
                : cfg.getSystemPrompt());
        String hint = INTENT_HINT.get(intent);
        if (hint != null) {
            sb.append("\n\n【本次提问的意图：").append(intent).append("】").append(hint);
        }
        if (!hits.isEmpty()) {
            sb.append("\n\n【本次可参考的门店知识库（请优先依据以下内容回答）】\n");
            int i = 1;
            for (AiKnowledge k : hits) {
                sb.append(i++).append(". 问：").append(k.getQuestion()).append("\n")
                        .append("   答：").append(k.getAnswer()).append("\n");
            }
            sb.append("若知识库未覆盖该问题，可基于常识建议，但要说明这是建议而非门店规定。\n");
        }
        sb.append("\n回答要求：简明口语化，200 字以内；不确定的信息不要编造，请引导用户联系前台。");
        return sb.toString();
    }

    /** 知识库兜底答案：明确告知用户这是降级结果，避免被误当成模型的标准回答 */
    private String knowledgeFallback(List<AiKnowledge> hits, String reason) {
        AiKnowledge best = hits.get(0);
        return "（" + reason + "，以下为门店知识库中的标准答案）\n" + best.getAnswer();
    }

    private String detectIntent(String q) {
        if (q == null) {
            return "other";
        }
        String s = q.toLowerCase();
        if (containsAny(s, "套餐", "价格", "多少钱", "费用", "月卡", "季卡", "年卡", "次卡", "续费", "到期", "划算")) {
            return "plan_query";
        }
        if (containsAny(s, "座位", "选座", "靠窗", "隔间", "双人", "位置", "占用", "座位图")) {
            return "seat_query";
        }
        if (containsAny(s, "预约", "取消", "爽约", "违规", "提前", "签到", "签退", "规则", "几点", "营业时间")) {
            return "rule_query";
        }
        if (containsAny(s, "学习计划", "复习计划", "备考计划", "学习安排", "时间安排", "规划", "计划",
                "怎么安排", "备考", "建议", "效率", "专注", "考研", "考公", "四六级", "提分")) {
            return "study_plan";
        }
        return "other";
    }

    private boolean containsAny(String s, String... words) {
        for (String w : words) {
            if (s.contains(w)) {
                return true;
            }
        }
        return false;
    }

    private record LlmResult(String content, int tokens) {
    }

    /** 调用 OpenAI 兼容的 /chat/completions；非 200 一律抛异常交给降级处理 */
    private LlmResult callLlm(AiConfig cfg, String apiKey, String systemPrompt, String question) throws Exception {
        String base = cfg.getBaseUrl() == null || cfg.getBaseUrl().isBlank()
                ? "https://api.deepseek.com" : cfg.getBaseUrl().trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String url = base + "/chat/completions";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", cfg.getModel() == null ? "deepseek-chat" : cfg.getModel());
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", question)));
        body.put("temperature", cfg.getTemperature() == null ? 0.7 : cfg.getTemperature().doubleValue());
        body.put("stream", false);

        int timeoutMs = cfg.getTimeoutMs() == null ? 20000 : cfg.getTimeoutMs();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(body), java.nio.charset.StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(
                java.nio.charset.StandardCharsets.UTF_8));

        if (resp.statusCode() != 200) {
            // 不把响应体原样抛给用户（可能含配额/账号等敏感信息），只记日志
            log.warn("大模型返回非 200：status={} body={}", resp.statusCode(),
                    resp.body() == null ? "" : resp.body().substring(0, Math.min(300, resp.body().length())));
            throw new IllegalStateException("大模型返回 HTTP " + resp.statusCode());
        }

        JsonNode root = objectMapper.readTree(resp.body());
        JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
        if (contentNode.isMissingNode() || contentNode.asText().isBlank()) {
            throw new IllegalStateException("大模型响应缺少 content 字段");
        }
        int tokens = root.path("usage").path("total_tokens").asInt(0);
        return new LlmResult(contentNode.asText().trim(), tokens);
    }

    private AiAnswerVO persistAndReturn(Long userId, String sessionId, String question, String answer,
                                        boolean degraded, String degradeReason, String intent,
                                        List<Long> hitIds, int tokens, long t0) {
        int cost = (int) (System.currentTimeMillis() - t0);
        try {
            AiChatRecord u = new AiChatRecord();
            u.setUserId(userId);
            u.setSessionId(sessionId);
            u.setRole("user");
            u.setContent(question);
            u.setIntent(intent);
            u.setDegraded(degraded ? 1 : 0);
            u.setTokens(0);
            u.setCostMs(0);
            u.setCreatedAt(LocalDateTime.now());
            chatMapper.insert(u);

            AiChatRecord a = new AiChatRecord();
            a.setUserId(userId);
            a.setSessionId(sessionId);
            a.setRole("assistant");
            a.setContent(answer);
            a.setIntent(intent);
            a.setDegraded(degraded ? 1 : 0);
            a.setTokens(tokens);
            a.setCostMs(cost);
            a.setCreatedAt(LocalDateTime.now());
            chatMapper.insert(a);
        } catch (Exception e) {
            // 落库失败也不能让用户拿不到答案
            log.warn("AI 对话记录写入失败：{}", e.getMessage());
        }
        return AiAnswerVO.builder()
                .sessionId(sessionId)
                .answer(answer)
                .degraded(degraded)
                .degradeReason(degradeReason)
                .intent(intent)
                .matchedKnowledgeIds(new ArrayList<>(new LinkedHashSet<>(hitIds)))
                .tokens(tokens)
                .costMs(cost)
                .build();
    }
}
