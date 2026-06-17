package com.nonu1l.media.service;

/**
 * AI provider 相关的轻量规则与 URL 处理。
 */
public final class AiProviderSupport {

    private AiProviderSupport() {
    }

    public static String inferProviderKind(String baseUrl) {
        String value = baseUrl == null ? "" : baseUrl.toLowerCase();
        if (value.contains("deepseek")) {
            return "deepseek";
        }
        if (value.contains("bigmodel") || value.contains("zhipu")) {
            return "glm";
        }
        if (value.contains("moonshot") || value.contains("kimi")) {
            return "kimi";
        }
        if (value.contains("minimax")) {
            return "minimax";
        }
        if (value.contains("mimo") || value.contains("momi") || value.contains("xiaomi")) {
            return "mimo";
        }
        if (value.contains("openai")) {
            return "openai";
        }
        return "custom";
    }

    public static String chatCompletionsUrl(String baseUrl) {
        return trimTrailingSlash(baseUrl) + "/chat/completions";
    }

    public static String trimTrailingSlash(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
