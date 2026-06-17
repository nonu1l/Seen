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
        if (value.contains("minimax")) {
            return "minimax";
        }
        if (value.contains("openai")) {
            return "openai";
        }
        return "custom";
    }

    /**
     * 判断当前 provider 是否使用通用 thinking 控制参数。
     *
     * <p>OpenAI 官方接口不接收该扩展参数；其他 OpenAI-compatible 服务默认启用，
     * 用于关闭 DeepSeek、GLM、MiniMax-M3 以及本地兼容网关的思考内容输出。</p>
     *
     * @param providerKind provider 类型
     * @return 需要补充 {@code thinking: {type: disabled}} 时返回 true
     */
    public static boolean usesThinkingToggle(String providerKind) {
        return !"openai".equalsIgnoreCase(providerKind);
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
