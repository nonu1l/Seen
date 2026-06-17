package com.nonu1l.media.service;

import com.nonu1l.media.service.thinking.CustomThinkingStrategy;
import com.nonu1l.media.service.thinking.DeepSeekThinkingStrategy;
import com.nonu1l.media.service.thinking.GlmThinkingStrategy;
import com.nonu1l.media.service.thinking.KimiThinkingStrategy;
import com.nonu1l.media.service.thinking.MiMoThinkingStrategy;
import com.nonu1l.media.service.thinking.MiniMaxThinkingStrategy;
import com.nonu1l.media.service.thinking.OpenAiThinkingStrategy;
import com.nonu1l.media.service.thinking.ThinkingMode;
import com.nonu1l.media.service.thinking.ThinkingStrategyRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThinkingStrategyRegistryTest {

    private final ThinkingStrategyRegistry registry = new ThinkingStrategyRegistry(List.of(
            new OpenAiThinkingStrategy(),
            new DeepSeekThinkingStrategy(),
            new GlmThinkingStrategy(),
            new KimiThinkingStrategy(),
            new MiniMaxThinkingStrategy(),
            new MiMoThinkingStrategy(),
            new CustomThinkingStrategy()
    ));

    @Test
    void openAiDoesNotInjectThinking() {
        Map<String, Object> body = registry.extraBody(setting("openai", "gpt-5"), ThinkingMode.DISABLED);

        assertTrue(body.isEmpty());
    }

    @Test
    void deepSeekGlmAndKimiDisableThinkingWithCommonType() {
        assertThinkingType("disabled", registry.extraBody(setting("deepseek", "deepseek-chat"), ThinkingMode.DISABLED));
        assertThinkingType("disabled", registry.extraBody(setting("glm", "glm-4.6"), ThinkingMode.DISABLED));
        assertThinkingType("disabled", registry.extraBody(setting("kimi", "kimi-k2"), ThinkingMode.DISABLED));
    }

    @Test
    void miniMaxUsesAdaptiveWhenEnabledOrAuto() {
        SettingsService.AiRuntimeSetting setting = setting("minimax", "MiniMax-M3");

        assertThinkingType("disabled", registry.extraBody(setting, ThinkingMode.DISABLED));
        assertThinkingType("adaptive", registry.extraBody(setting, ThinkingMode.ENABLED));
        assertThinkingType("adaptive", registry.extraBody(setting, ThinkingMode.AUTO));
    }

    @Test
    void mimoOnlyInjectsDisabledMode() {
        SettingsService.AiRuntimeSetting setting = setting("mimo", "MiMo");

        assertThinkingType("disabled", registry.extraBody(setting, ThinkingMode.DISABLED));
        assertTrue(registry.extraBody(setting, ThinkingMode.ENABLED).isEmpty());
        assertTrue(registry.extraBody(setting, ThinkingMode.AUTO).isEmpty());
    }

    @Test
    void customProviderDoesNotInjectThinking() {
        Map<String, Object> body = registry.extraBody(setting("custom", "unknown-model"), ThinkingMode.DISABLED);

        assertTrue(body.isEmpty());
    }

    @Test
    void cacheKeyIncludesThinkingModeAndExtraBody() {
        AiChatClientFactory factory = new AiChatClientFactory(null, null, registry);
        SettingsService.AiRuntimeSetting setting = setting("minimax", "MiniMax-M3");

        String disabled = factory.cacheKey(setting, ThinkingMode.DISABLED,
                registry.extraBody(setting, ThinkingMode.DISABLED));
        String auto = factory.cacheKey(setting, ThinkingMode.AUTO,
                registry.extraBody(setting, ThinkingMode.AUTO));

        assertNotEquals(disabled, auto);
    }

    @SuppressWarnings("unchecked")
    private static void assertThinkingType(String expected, Map<String, Object> body) {
        Map<String, Object> thinking = (Map<String, Object>) body.get("thinking");
        assertEquals(expected, thinking.get("type"));
    }

    private static SettingsService.AiRuntimeSetting setting(String providerKind, String model) {
        return new SettingsService.AiRuntimeSetting(null, providerKind,
                "https://example.com/v1", "key", model, 0.0d);
    }
}
