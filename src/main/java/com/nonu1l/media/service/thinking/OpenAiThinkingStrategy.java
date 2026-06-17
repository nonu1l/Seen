package com.nonu1l.media.service.thinking;

import com.nonu1l.media.service.SettingsService;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * OpenAI 官方接口策略；官方 Chat Completions 不注入各家兼容扩展的 thinking 字段。
 */
@Component
public class OpenAiThinkingStrategy extends AbstractThinkingStrategy {

    @Override
    public boolean supports(SettingsService.AiRuntimeSetting setting) {
        return matchesProvider(setting, "openai");
    }

    @Override
    public Map<String, Object> extraBody(SettingsService.AiRuntimeSetting setting, ThinkingMode mode) {
        return Map.of();
    }

    @Override
    public int order() {
        return 0;
    }
}
