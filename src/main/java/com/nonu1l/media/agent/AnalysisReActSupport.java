package com.nonu1l.media.agent;

import com.nonu1l.media.service.IntentAnalysisService;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Analysis ReAct 循环的纯辅助逻辑：解析模型决策、截断观察内容并组装调试内容块。
 */
final class AnalysisReActSupport {

    private static final String TRUNCATED_SUFFIX = "\n...[截断]";

    private AnalysisReActSupport() {
    }

    /**
     * @param webEnabled 搜索源是否可用
     * @param hasUrl 用户输入是否包含明确 URL
     * @return Analysis ReAct 本轮允许暴露的只读工具名
     */
    static Set<String> allowedToolNames(boolean webEnabled, boolean hasUrl) {
        Set<String> names = new LinkedHashSet<>();
        names.add("searchLocal");
        names.add("getWorkState");
        names.add("readUserMemory");
        if (webEnabled) {
            names.add("searchWeb");
            names.add("fetchWeb");
        } else if (hasUrl) {
            names.add("fetchWeb");
        }
        return names;
    }

    /**
     * 解析 LLM 产出的 ReAct 决策 JSON，并校验工具是否在白名单内。
     *
     * @param rawContent LLM 原始文本
     * @param objectMapper JSON 映射工具
     * @param allowedTools 本轮允许调用的工具名
     * @return 结构化决策
     */
    @SuppressWarnings("unchecked")
    static AnalysisReActDecision parseDecision(String rawContent, ObjectMapper objectMapper,
                                               Set<String> allowedTools) {
        try {
            String json = IntentAnalysisService.extractJsonObject(rawContent);
            Map<String, Object> root = objectMapper.readValue(json, Map.class);
            Object rawType = root.get("type");
            String type = rawType != null ? String.valueOf(rawType).trim().toLowerCase(Locale.ROOT) : "";
            if ("final".equals(type)) {
                String answer = textValue(root.get("answer"));
                if (answer.isBlank()) {
                    throw new IllegalArgumentException("final answer is blank");
                }
                return AnalysisReActDecision.finalAnswer(answer);
            }
            if ("tool".equals(type)) {
                String tool = textValue(root.get("tool"));
                if (tool.isBlank()) {
                    throw new IllegalArgumentException("tool name is blank");
                }
                if (allowedTools == null || !allowedTools.contains(tool)) {
                    throw new IllegalArgumentException("tool is not allowed: " + tool);
                }
                Object rawInput = root.get("input");
                if (!(rawInput instanceof Map<?, ?> rawMap)) {
                    throw new IllegalArgumentException("tool input must be an object");
                }
                Map<String, Object> input = new LinkedHashMap<>();
                rawMap.forEach((key, value) -> {
                    if (key != null) {
                        input.put(String.valueOf(key), value);
                    }
                });
                if (input.isEmpty()) {
                    throw new IllegalArgumentException("tool input is empty");
                }
                return AnalysisReActDecision.toolCall(tool, input);
            }
            throw new IllegalArgumentException("unknown decision type: " + type);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("failed to parse ReAct decision", e);
        }
    }

    /**
     * @param text 原始文本
     * @param maxChars 最大字符数
     * @return 截断后的文本，长度不超过 maxChars
     */
    static String limitText(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        int limit = Math.max(1, maxChars);
        if (text.length() <= limit) {
            return text;
        }
        if (limit <= TRUNCATED_SUFFIX.length()) {
            return text.substring(0, limit);
        }
        return text.substring(0, limit - TRUNCATED_SUFFIX.length()) + TRUNCATED_SUFFIX;
    }

    /**
     * 将工具观察整理为可重新喂给 LLM 的 scratchpad。
     *
     * @param steps 已执行的 ReAct 工具步骤
     * @param maxChars scratchpad 最大长度
     * @return 工具观察摘要
     */
    static String scratchpad(List<AnalysisReActTraceStep> steps, int maxChars) {
        if (steps == null || steps.isEmpty()) {
            return "（暂无工具观察）";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < steps.size(); i++) {
            AnalysisReActTraceStep step = steps.get(i);
            sb.append("第 ").append(i + 1).append(" 步工具：").append(step.tool()).append('\n')
                    .append("输入：").append(step.inputJson()).append('\n');
            if (step.error() != null && !step.error().isBlank()) {
                sb.append("错误：").append(step.error()).append('\n');
            }
            sb.append("观察：").append(step.observation()).append("\n\n");
        }
        String text = sb.toString().trim();
        int limit = Math.max(1, maxChars);
        if (text.length() <= limit) {
            return text;
        }
        String prefix = "[前序观察已截断]\n";
        if (limit <= prefix.length()) {
            return text.substring(text.length() - limit);
        }
        return prefix + text.substring(text.length() - (limit - prefix.length()));
    }

    /**
     * 组装前端可展示的 text/tool_use/tool_result 调试内容块。
     *
     * @param objectMapper JSON 映射工具
     * @param finalText 最终回复正文
     * @param steps ReAct 工具调用步骤
     * @return content blocks JSON
     */
    static String contentBlocksJson(ObjectMapper objectMapper, String finalText,
                                    List<AnalysisReActTraceStep> steps) {
        List<Map<String, Object>> blocks = new ArrayList<>();
        blocks.add(Map.of("type", "text", "text", finalText != null ? finalText : ""));
        if (steps != null) {
            for (int i = 0; i < steps.size(); i++) {
                AnalysisReActTraceStep step = steps.get(i);
                String id = "analysis-react-" + (i + 1);
                Map<String, Object> toolUse = new LinkedHashMap<>();
                toolUse.put("type", "tool_use");
                toolUse.put("id", id);
                toolUse.put("name", step.tool());
                toolUse.put("input", step.inputJson());
                blocks.add(toolUse);

                Map<String, Object> toolResult = new LinkedHashMap<>();
                toolResult.put("type", "tool_result");
                toolResult.put("tool_use_id", id);
                toolResult.put("content", step.error() != null && !step.error().isBlank()
                        ? step.error()
                        : step.observation());
                blocks.add(toolResult);
            }
        }
        try {
            return objectMapper.writeValueAsString(blocks);
        } catch (Exception e) {
            return "[{\"type\":\"text\",\"text\":\"" + escapeJson(finalText) + "\"}]";
        }
    }

    /**
     * 将模型字段值归一化为去空白字符串。
     */
    private static String textValue(Object value) {
        return value != null ? String.valueOf(value).trim() : "";
    }

    /**
     * 为极端序列化失败兜底构造最小 JSON 字符串。
     */
    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    /**
     * Analysis ReAct 中模型返回的下一步动作。
     */
    record AnalysisReActDecision(String type, String tool, Map<String, Object> input, String answer) {

        /**
         * @return 调用指定工具的决策。
         */
        static AnalysisReActDecision toolCall(String tool, Map<String, Object> input) {
            return new AnalysisReActDecision("tool", tool, input, null);
        }

        /**
         * @return 直接返回最终回答的决策。
         */
        static AnalysisReActDecision finalAnswer(String answer) {
            return new AnalysisReActDecision("final", null, Map.of(), answer);
        }

        /**
         * @return 当前决策是否已经进入最终回答。
         */
        boolean isFinal() {
            return "final".equals(type);
        }
    }

    /**
     * Analysis ReAct 中一次工具调用及其观察摘要。
     */
    record AnalysisReActTraceStep(String tool, String inputJson, String observation, String error) {
    }
}
