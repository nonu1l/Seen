package com.nonu1l.media.service.thinking;

import com.nonu1l.media.service.SettingsService;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MiniMax OpenAI-compatible 策略，关闭使用 disabled，开启或自动使用 adaptive。
 */
@Component
public class MiniMaxThinkingStrategy extends AbstractThinkingStrategy {

    @Override
    public boolean supports(SettingsService.AiRuntimeSetting setting) {
        return matchesProvider(setting, "minimax") || modelContains(setting, "minimax");
    }

    @Override
    public Map<String, Object> extraBody(SettingsService.AiRuntimeSetting setting, ThinkingMode mode) {
        ThinkingMode resolved = resolveMode(setting, mode);
        if (resolved == ThinkingMode.DISABLED) {
            return thinkingType("disabled");
        }
        if (resolved == ThinkingMode.ENABLED || resolved == ThinkingMode.AUTO) {
            return thinkingType("adaptive");
        }
        return Map.of();
    }

    @Override
    public int order() {
        return 40;
    }
}
