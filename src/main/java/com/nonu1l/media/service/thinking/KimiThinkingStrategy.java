package com.nonu1l.media.service.thinking;

import com.nonu1l.media.service.SettingsService;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Kimi/Moonshot OpenAI-compatible 策略，使用 {@code thinking.type} 控制支持的思考模型。
 */
@Component
public class KimiThinkingStrategy extends AbstractThinkingStrategy {

    @Override
    public boolean supports(SettingsService.AiRuntimeSetting setting) {
        return matchesProvider(setting, "kimi") || modelContains(setting, "kimi", "moonshot");
    }

    @Override
    public Map<String, Object> extraBody(SettingsService.AiRuntimeSetting setting, ThinkingMode mode) {
        ThinkingMode resolved = resolveMode(setting, mode);
        if (resolved == ThinkingMode.DISABLED) {
            return thinkingType("disabled");
        }
        if (resolved == ThinkingMode.ENABLED) {
            return thinkingType("enabled");
        }
        return Map.of();
    }

    @Override
    public int order() {
        return 30;
    }
}
