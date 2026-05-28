package com.nonu1l.media.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
                                  BangumiTools tools) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
        this.tools = tools;
        this.agentPrompt = loadPrompt("prompts/agent-system.st");
    }

    /**
     * 一次 @Tool ChatClient 调用完成意图分析。
     */
    public IntentAnalysisResult analyze(String userInput, String history) {
        String system = agentPrompt.replace("{history}",
                history != null && !history.isBlank() ? history : "（无历史）");

        log.debug("Agent call: userInput={}", userInput);

        try {
            String content = chatClient.prompt()
                    .system(system)
                    .user(userInput)
                    .toolCallbacks(
                            searchBangumiCallback(),
                            searchLocalCallback(),
                            searchWebCallback())
                    .call()
                    .content();

            if (content == null || content.isBlank()) {
                throw new IllegalStateException("Agent returned empty content (tool-call loop may have aborted)");
            }

            String json = extractJsonObject(content);
            AgentOutput output = objectMapper.readValue(json, new TypeReference<AgentOutput>() {});

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
                .description("DuckDuckGo 网页搜索。用于根据描述找片名或搜索推荐。")
                .inputType(SearchReq.class)
                .build();
    }

    /** FunctionCallback 需要 POJO 输入类型 */
    public record SearchReq(String keyword) {}

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
