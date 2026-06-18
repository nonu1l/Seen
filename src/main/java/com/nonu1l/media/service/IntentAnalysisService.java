package com.nonu1l.media.service;

/**
 * JSON 提取工具 — Agent 调用中 LLM 输出解析的公共方法。
 * 目前保留 JSON 对象提取能力，供长期记忆等结构化 LLM 输出解析复用。
 */
public class IntentAnalysisService {

    /**
     * 从 LLM 返回文本中提取首个完整 JSON 对象。
     * 通过括号配对（跳过字符串字面量与转义符）定位结尾。
     *
     * @param text LLM 原始响应
     * @return 修剪后的 JSON 片段字符串
     * @throws IllegalArgumentException 输入为空、未找到对象或括号不平衡时抛出
     */
    public static String extractJsonObject(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Empty agent content");
        }
        String stripped = text.replaceAll("(?s)```(?:json)?\\s*", "").replace("```", "").trim();
        int start = stripped.indexOf('{');
        if (start < 0) {
            throw new IllegalArgumentException("No JSON object found");
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
        throw new IllegalArgumentException("Unbalanced JSON braces");
    }
}
