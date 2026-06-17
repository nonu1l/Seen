package com.nonu1l.media.service;

/**
 * JSON 提取与修复工具 — Agent 调用中 LLM 输出解析的公共方法。
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

    /**
     * 修复 JSON 字符串值内部未转义的双引号。
     * 状态机遍历：当在字符串内遇到 " 时，向前看下一个非空白字符 —
     * 若是 , } ] : 则认为是真正的 JSON 字符串终结符；
     * 否则认为是未转义的内容引号，补上反斜杠。
     *
     * @param content 原始 JSON 文本
     * @return 修复后的 JSON 文本
     */
    public static String repairUnescapedQuotesInJsonStrings(String content) {
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

    private static boolean isJsonStringTerminator(String content, int start) {
        for (int i = start; i < content.length(); i++) {
            char next = content.charAt(i);
            if (Character.isWhitespace(next)) continue;
            return next == ',' || next == '}' || next == ']' || next == ':';
        }
        return true;
    }
}
