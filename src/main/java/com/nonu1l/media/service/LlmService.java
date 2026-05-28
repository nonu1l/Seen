//package com.nonu1l.media.service;
//
//import com.nonu1l.media.model.dto.ParseResult;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.ai.chat.client.ChatClient;
//import org.springframework.ai.converter.BeanOutputConverter;
//import org.springframework.core.io.ClassPathResource;
//import org.springframework.stereotype.Service;
//
//import java.io.IOException;
//import java.nio.charset.StandardCharsets;
//import java.util.Map;
//import java.util.regex.Pattern;
//
///**
// * LLM 服务（已弃用，仅保留代码）。
// * 原用于通过 AI 解析用户自然语言输入，提取搜索关键词、评分和评价。
// * 现已改用 Bangumi 搜索 + 手动标记流程。
// */
//@Service
//public class LlmService {
//
//    private static final Logger log = LoggerFactory.getLogger(LlmService.class);
//    private static final int MAX_ATTEMPTS = 3;
//    /** 去掉搜索词末尾的「- 副标题」部分 */
//    private static final Pattern SEASON_SUFFIX = Pattern.compile("[\\s]*[:\\-–—][\\s].+$");
//
//    private static final String STRICT_JSON_INSTRUCTION = """
//            请仅返回可被 JSON 解析器直接解析的 JSON 对象，并严格满足字段结构要求：
//            1) 不要输出 Markdown 代码块（如 ```json）。
//            2) 不要输出任何解释文字、前后缀、注释。
//            3) 所有字符串内引号必须正确转义。
//            """;
//
//    private final ChatClient chatClient;
//    private final BeanOutputConverter<ParseResult> parseConverter;
//    private final BeanOutputConverter<PolishResult> polishConverter;
//
//    private final String parseSystemPrompt;
//    private final String parseUserTemplate;
//    private final String polishSystemPrompt;
//    private final String polishUserTemplate;
//
//    record PolishResult(String review, int rating) {}
//
//    public LlmService(ChatClient.Builder chatClientBuilder) {
//        this.chatClient = chatClientBuilder.build();
//        this.parseConverter = new BeanOutputConverter<>(ParseResult.class);
//        this.polishConverter = new BeanOutputConverter<>(PolishResult.class);
//        this.parseSystemPrompt = loadPrompt("prompts/parse-input-system.st");
//        this.parseUserTemplate = loadPrompt("prompts/parse-input-user.st");
//        this.polishSystemPrompt = loadPrompt("prompts/polish-system.st");
//        this.polishUserTemplate = loadPrompt("prompts/polish-user.st");
//    }
//
//    // ── 解析用户输入 ─────────────────────────────────────────────────
//
//    /**
//     * 将用户原始输入解析为结构化查询（已弃用）。
//     * 最大重试 3 次，失败时回退将整个输入当作搜索词。
//     */
//    public ParseResult parseInput(String input) {
//        String baseSystem = parseSystemPrompt + "\n\n" + parseConverter.getFormat();
//        String userMessage = parseUserTemplate.replace("{input}", input);
//
//        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
//            try {
//                String system = attempt == 1 ? baseSystem : baseSystem + "\n\n" + STRICT_JSON_INSTRUCTION;
//                String content = chatClient.prompt()
//                        .system(system)
//                        .user(userMessage)
//                        .call()
//                        .content();
//                ParseResult result = parseConverter.convert(cleanJson(content));
//                if (result.getSearchQuery() != null) {
//                    result.setSearchQuery(SEASON_SUFFIX.matcher(result.getSearchQuery()).replaceFirst("").trim());
//                }
//                return result;
//            } catch (Exception e) {
//                if (attempt < MAX_ATTEMPTS) {
//                    log.warn("parseInput 解析失败，重试: attempt={}/{}, error={}", attempt, MAX_ATTEMPTS, e.getMessage());
//                } else {
//                    log.error("parseInput 已达最大重试次数: attempts={}", MAX_ATTEMPTS, e);
//                }
//            }
//        }
//        ParseResult fallback = new ParseResult();
//        fallback.setSearchQuery(input);
//        return fallback;
//    }
//
//    // ── 润色评价 ─────────────────────────────────────────────────
//
//    /**
//     * 将用户原始评价 + TMDB 信息润色为正式评价（已弃用）。
//     * 最大重试 3 次，失败时回退返回原始输入。
//     */
//    public Map<String, Object> polish(String rawInput, String tmdbInfo) {
//        String baseSystem = polishSystemPrompt + "\n\n" + polishConverter.getFormat();
//        String userMessage = polishUserTemplate
//                .replace("{rawInput}", rawInput)
//                .replace("{tmdbInfo}", tmdbInfo);
//
//        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
//            try {
//                String system = attempt == 1 ? baseSystem : baseSystem + "\n\n" + STRICT_JSON_INSTRUCTION;
//                String content = chatClient.prompt()
//                        .system(system)
//                        .user(userMessage)
//                        .call()
//                        .content();
//                PolishResult r = polishConverter.convert(cleanJson(content));
//                return Map.of("review", r.review(), "rating", r.rating());
//            } catch (Exception e) {
//                if (attempt < MAX_ATTEMPTS) {
//                    log.warn("polish 解析失败，重试: attempt={}/{}, error={}", attempt, MAX_ATTEMPTS, e.getMessage());
//                } else {
//                    log.error("polish 已达最大重试次数: attempts={}", MAX_ATTEMPTS, e);
//                }
//            }
//        }
//        return Map.of("review", rawInput, "rating", 0);
//    }
//
//    // ── 内部工具方法 ──────────────────────────────────────────────
//
//    /** 从 classpath 加载提示词模板文件 */
//    private String loadPrompt(String path) {
//        try {
//            return new String(new ClassPathResource(path).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
//        } catch (IOException e) {
//            throw new RuntimeException("Failed to load prompt: " + path, e);
//        }
//    }
//
//    /** 清理 LLM 返回的 JSON：移除 markdown 代码块标记、修复格式 */
//    private String cleanJson(String text) {
//        if (text == null || text.isBlank()) return "{}";
//        text = text.replaceAll("(?s)```(?:json)?\\s*", "").trim();
//        text = text.replaceAll(",\\s*}", "}");
//        text = text.replaceAll(",\\s*]", "]");
//        text = repairQuotes(text);
//        return text;
//    }
//
//    /** 修复 JSON 中未正确转义的引号 */
//    private String repairQuotes(String content) {
//        StringBuilder sb = new StringBuilder(content.length() + 16);
//        boolean inString = false;
//        boolean escaping = false;
//        for (int i = 0; i < content.length(); i++) {
//            char ch = content.charAt(i);
//            if (!inString) {
//                if (ch == '"') inString = true;
//                sb.append(ch);
//                continue;
//            }
//            if (escaping) { sb.append(ch); escaping = false; continue; }
//            if (ch == '\\') { sb.append(ch); escaping = true; continue; }
//            if (ch == '"') {
//                if (isTerminator(content, i + 1)) { inString = false; sb.append(ch); }
//                else { sb.append("\\\""); }
//                continue;
//            }
//            sb.append(ch);
//        }
//        return sb.toString();
//    }
//
//    /** 判断当前位置是否为 JSON 值结束符（逗号、大括号、中括号、冒号） */
//    private boolean isTerminator(String content, int start) {
//        for (int i = start; i < content.length(); i++) {
//            char c = content.charAt(i);
//            if (Character.isWhitespace(c)) continue;
//            return c == ',' || c == '}' || c == ']' || c == ':';
//        }
//        return true;
//    }
//}
