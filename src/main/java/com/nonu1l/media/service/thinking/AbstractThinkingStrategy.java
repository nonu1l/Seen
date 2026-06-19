package com.nonu1l.media.service.thinking;

import com.nonu1l.media.service.SettingsService;

import java.util.Locale;
import java.util.Map;

/**
 * 思考模式策略基类，提供 AI Base URL 匹配和常见 thinking body 构造方法。
 */
abstract class AbstractThinkingStrategy implements ThinkingStrategy {

    /**
     * 判断 AI Base URL 是否包含任一 provider 关键词。
     *
     * @param setting 当前 AI 运行配置
     * @param hints provider URL 关键词
     * @return 命中时返回 true
     */
    protected boolean baseUrlContains(SettingsService.AiRuntimeSetting setting, String... hints) {
        String baseUrl = normalize(setting.baseUrl());
        for (String hint : hints) {
            if (!hint.isBlank() && baseUrl.contains(normalize(hint))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 构造通用 {@code thinking.type} body。
     *
     * @param type provider 支持的 thinking type
     * @return 可直接传给 OpenAiChatOptions.extraBody 的 map
     */
    protected Map<String, Object> thinkingType(String type) {
        return Map.of("thinking", Map.of("type", type));
    }

    /**
     * 未指定模式时使用策略默认模式。
     *
     * @param setting 当前 AI 运行配置
     * @param mode 原始模式
     * @return 解析后的模式
     */
    protected ThinkingMode resolveMode(SettingsService.AiRuntimeSetting setting, ThinkingMode mode) {
        return mode == null ? defaultMode(setting) : mode;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }
}
