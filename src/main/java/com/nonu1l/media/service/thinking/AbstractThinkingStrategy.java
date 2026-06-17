package com.nonu1l.media.service.thinking;

import com.nonu1l.media.service.SettingsService;

import java.util.Locale;
import java.util.Map;

/**
 * 思考模式策略基类，提供 provider/model 匹配和常见 thinking body 构造方法。
 */
abstract class AbstractThinkingStrategy implements ThinkingStrategy {

    /**
     * 判断 providerKind 或 model 名称是否命中当前策略。
     *
     * @param setting 当前 AI 运行配置
     * @param providerKinds 支持的 providerKind 列表
     * @return 命中时返回 true
     */
    protected boolean matchesProvider(SettingsService.AiRuntimeSetting setting, String... providerKinds) {
        String provider = normalize(setting.providerKind());
        for (String providerKind : providerKinds) {
            if (provider.equals(normalize(providerKind))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断 model 名称是否包含任一关键词。
     *
     * @param setting 当前 AI 运行配置
     * @param hints model 关键词
     * @return 命中时返回 true
     */
    protected boolean modelContains(SettingsService.AiRuntimeSetting setting, String... hints) {
        String model = normalize(setting.model());
        for (String hint : hints) {
            if (!hint.isBlank() && model.contains(normalize(hint))) {
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
     * 将 DEFAULT 解析为策略默认模式。
     *
     * @param setting 当前 AI 运行配置
     * @param mode 原始模式
     * @return 解析后的模式
     */
    protected ThinkingMode resolveMode(SettingsService.AiRuntimeSetting setting, ThinkingMode mode) {
        return mode == null || mode == ThinkingMode.DEFAULT ? defaultMode(setting) : mode;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }
}
