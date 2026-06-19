package com.nonu1l.media.service.thinking;

import com.nonu1l.media.service.SettingsService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * MiniMax OpenAI-compatible 策略，关闭使用 disabled，开启使用 adaptive。
 */
@Component
public class MiniMaxThinkingStrategy extends AbstractThinkingStrategy {

    private static final Pattern THINK_BLOCK = Pattern.compile("(?is)<think>.*?</think>\\s*");

    @Override
    public boolean supports(SettingsService.AiRuntimeSetting setting) {
        return baseUrlContains(setting, "minimax");
    }

    @Override
    public Map<String, Object> extraBody(SettingsService.AiRuntimeSetting setting, ThinkingMode mode) {
        ThinkingMode resolved = resolveMode(setting, mode);
        if (resolved == ThinkingMode.DISABLED) {
            return thinkingType("disabled");
        }
        if (resolved == ThinkingMode.ENABLED) {
            return thinkingType("adaptive");
        }
        return Map.of();
    }

    @Override
    public String cleanAssistantContent(String content) {
        return content == null ? null : THINK_BLOCK.matcher(content).replaceAll("").trim();
    }

    @Override
    public String providerName() {
        return "minimax";
    }
}
