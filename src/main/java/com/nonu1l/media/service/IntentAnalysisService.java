package com.nonu1l.media.service;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nonu1l.media.config.TokenUsageAdvisor;
import com.nonu1l.media.model.dto.IntentAnalysisResult;
import com.nonu1l.media.model.dto.MatchedEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 意图分析 Service
 *
 * @author nonu1l
 * @date 2026/05/28
 */
@Service
public class IntentAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(IntentAnalysisService.class);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final BangumiTools tools;
    private final String agentPrompt;

    public IntentAnalysisService(ChatClient.Builder chatClientBuilder,
                                  ObjectMapper objectMapper,
                                  BangumiTools tools,
                                  TokenUsageAdvisor tokenUsageAdvisor) {
        this.chatClient = chatClientBuilder.defaultAdvisors(tokenUsageAdvisor).build();
        this.objectMapper = objectMapper;
        this.tools = tools;
        this.agentPrompt = loadPrompt("prompts/agent-system.st");
    }

    /**
     * 一次 @Tool ChatClient 调用完成意图分析。
     */
    public IntentAnalysisResult analyze(String userInput, String history) {
        String system = agentPrompt
                .replace("{today}", java.time.LocalDate.now().toString())
                .replace("{history}",
                        history != null && !history.isBlank() ? history : "（无历史）");

        log.debug("Agent call: userInput={}", userInput);

        try {
            String content = chatClient.prompt()
                    .system(system)
                    .user(userInput)
                    .toolCallbacks(
                            searchBangumiCallback(),
                            searchLocalCallback(),
                            searchWebCallback(),
                            fetchWebCallback(),
                            trendingBangumiCallback())
                    .call()
                    .content();

            if (content == null || content.isBlank()) {
                throw new IllegalStateException("Agent returned empty content (tool-call loop may have aborted)");
            }

            String json = extractJsonObject(content);
            AgentOutput output = parseAgentOutput(json);

            return new IntentAnalysisResult(
                    output.replyText(),
                    output.cards() != null ? output.cards() : List.of(),
                    output.unmarkIds() != null ? output.unmarkIds() : List.of());

        } catch (Exception e) {
            log.error("Agent analysis failed", e);
            return new IntentAnalysisResult(null, List.of(), List.of());
        }
    }

    // ── Tool Callbacks ──────────────────────────────────────

    private ToolCallback searchBangumiCallback() {
        return FunctionToolCallback.builder("searchBangumi",
                        (SearchReq req) -> tools.searchBangumi(req.keyword()))
                .description("搜索 Bangumi 影视数据库。传入中文或英文片名关键词。")
                .inputType(SearchReq.class)
                .build();
    }

    private ToolCallback searchLocalCallback() {
        return FunctionToolCallback.builder("searchLocal",
                        (SearchReq req) -> tools.searchLocal(req.keyword()))
                .description("查询本地已标记的作品记录。keyword 可选，不传返回全部。")
                .inputType(SearchReq.class)
                .build();
    }

    private ToolCallback searchWebCallback() {
        return FunctionToolCallback.builder("searchWeb",
                        (SearchReq req) -> tools.searchWeb(req.keyword()))
                .description("搜索引擎，返回标题+摘要+URL列表。用于根据描述找片名、推荐、热门排行等。")
                .inputType(SearchReq.class)
                .build();
    }

    private ToolCallback fetchWebCallback() {
        return FunctionToolCallback.builder("fetchWeb",
                        (FetchReq req) -> tools.fetchWeb(req.url()))
                .description("抓取网页纯文本内容。当搜索结果摘要信息不足时，用此方法打开链接阅读详情以提取片名或信息。")
                .inputType(FetchReq.class)
                .build();
    }

    private ToolCallback trendingBangumiCallback() {
        return FunctionToolCallback.builder("trendingBangumi",
                        (TrendingReq req) -> tools.trendingBangumi(req.type(), req.year()))
                .description("获取 Bangumi 趋势热门排行。type=2 动画 / 6 真人，year 可选筛选年份。用户问当前热门/排行榜时优先用此工具。")
                .inputType(TrendingReq.class)
                .build();
    }

    /** FunctionCallback 需要 POJO 输入类型 */
    public record SearchReq(String keyword) {}
    public record FetchReq(String url) {}
    public record TrendingReq(int type, Integer year) {}

    /** LLM 输出的 JSON 结构 */
    public record AgentOutput(String replyText, List<MatchedEntry> cards, List<Long> unmarkIds) {}

    // ── helpers ──────────────────────────────────────────────

    /**
     * 从 LLM 返回文本中提取首个完整 JSON 对象。
     * 通过括号配对（跳过字符串字面量与转义符）定位结尾，避免被前后说明里的 } 干扰。
     */
    static String extractJsonObject(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Empty agent content");
        }
        String stripped = text.replaceAll("(?s)```(?:json)?\\s*", "").replace("```", "").trim();
        int start = stripped.indexOf('{');
        if (start < 0) {
            throw new IllegalArgumentException("No JSON object found in: " + truncate(text));
        }

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < stripped.length(); i++) {
            char c = stripped.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return stripped.substring(start, i + 1);
                }
            }
        }
        throw new IllegalArgumentException("Unbalanced JSON braces in: " + truncate(text));
    }

    /**
     * 解析 AgentOutput JSON。首次失败后尝试修复未转义引号再重试。
     * LLM 常见错误：replyText 中直接使用 " 做中文引号，导致 JSON 字符串提前闭合。
     */
    private AgentOutput parseAgentOutput(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<AgentOutput>() {});
        } catch (Exception firstError) {
            String repaired = repairUnescapedQuotesInJsonStrings(json);
            if (!repaired.equals(json)) {
                try {
                    AgentOutput result = objectMapper.readValue(repaired, new TypeReference<AgentOutput>() {});
                    log.warn("Agent JSON 存在未转义引号，已本地修复后解析成功");
                    return result;
                } catch (Exception e) {
                    log.warn("修复后仍然无法解析 Agent JSON: {}", e.getMessage());
                }
            }
            throw new RuntimeException("Failed to parse AgentOutput JSON", firstError);
        }
    }

    /**
     * 修复 JSON 字符串值内部未转义的双引号。
     * 状态机遍历：当在字符串内遇到 " 时，向前看下一个非空白字符 —
     * 若是 , } ] : 则认为是真正的 JSON 字符串终结符；
     * 否则认为是未转义的内容引号，补上反斜杠。
     */
    static String repairUnescapedQuotesInJsonStrings(String content) {
        if (content == null || content.isBlank()) return content;
        StringBuilder out = new StringBuilder(content.length() + 16);
        boolean inString = false;
        boolean escaping = false;
        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (!inString) {
                if (ch == '"') inString = true;
                out.append(ch);
                continue;
            }
            if (escaping) {
                out.append(ch);
                escaping = false;
                continue;
            }
            if (ch == '\\') {
                out.append(ch);
                escaping = true;
                continue;
            }
            if (ch == '"') {
                if (isJsonStringTerminator(content, i + 1)) {
                    inString = false;
                    out.append(ch);
                } else {
                    out.append("\\\"");
                }
                continue;
            }
            out.append(ch);
        }
        return out.toString();
    }

    /** 向前跳过空白，判断下一个字符是否为 JSON 结构终止符 */
    private static boolean isJsonStringTerminator(String content, int start) {
        for (int i = start; i < content.length(); i++) {
            char next = content.charAt(i);
            if (Character.isWhitespace(next)) continue;
            return next == ',' || next == '}' || next == ']' || next == ':';
        }
        return true; // 字符串结尾 — 认为该引号是终结符
    }

    private static String truncate(String s) {
        return s.length() <= 200 ? s : s.substring(0, 200) + "...";
    }

    private String loadPrompt(String path) {
        try {
            return new String(new ClassPathResource(path).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load prompt: " + path, e);
        }
    }
}
