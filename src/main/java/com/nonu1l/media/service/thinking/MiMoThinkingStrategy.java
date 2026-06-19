package com.nonu1l.media.service.thinking;

import com.nonu1l.media.service.SettingsService;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MiMo OpenAI-compatible 策略，仅在关闭思考模式时注入 disabled，其他模式保留 provider 默认行为。
 */
@Component
public class MiMoThinkingStrategy extends AbstractThinkingStrategy {

    @Override
    public boolean supports(SettingsService.AiRuntimeSetting setting) {
        return baseUrlContains(setting, "mimo", "momi", "xiaomi");
    }

    @Override
    public Map<String, Object> extraBody(SettingsService.AiRuntimeSetting setting, ThinkingMode mode) {
        ThinkingMode resolved = resolveMode(setting, mode);
        if (resolved == ThinkingMode.DISABLED) {
            return thinkingType("disabled");
        }
        return Map.of();
    }

    @Override
    public String providerName() {
        return "mimo";
    }
}
