package com.nonu1l.media.service.thinking;

import com.nonu1l.media.service.SettingsService;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 未识别 OpenAI-compatible provider 的保守兜底策略，不注入非标准 thinking 字段。
 */
@Component
public class CustomThinkingStrategy extends AbstractThinkingStrategy {

    @Override
    public boolean supports(SettingsService.AiRuntimeSetting setting) {
        return true;
    }

    @Override
    public Map<String, Object> extraBody(SettingsService.AiRuntimeSetting setting, ThinkingMode mode) {
        return Map.of();
    }

    @Override
    public boolean fallback() {
        return true;
    }
}
