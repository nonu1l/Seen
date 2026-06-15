package com.nonu1l.media.service;

import com.nonu1l.media.config.TokenUsageAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 根据当前 AI 接入配置动态创建和复用 ChatClient。
 */
@Service
public class AiChatClientFactory {

    private final SettingsService settingsService;
    private final TokenUsageAdvisor tokenUsageAdvisor;
    private final ConcurrentHashMap<String, ChatClient> cache = new ConcurrentHashMap<>();

    public AiChatClientFactory(SettingsService settingsService,
                               TokenUsageAdvisor tokenUsageAdvisor) {
        this.settingsService = settingsService;
        this.tokenUsageAdvisor = tokenUsageAdvisor;
    }

    public ChatClient currentClient() {
        SettingsService.AiRuntimeSetting setting = settingsService.currentRuntimeSetting();
        validate(setting);
        String cacheKey = cacheKey(setting);
        return cache.computeIfAbsent(cacheKey, ignored -> buildClient(setting));
    }

    private ChatClient buildClient(SettingsService.AiRuntimeSetting setting) {
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder()
                .baseUrl(AiProviderSupport.trimTrailingSlash(setting.baseUrl()))
                .apiKey(setting.apiKey())
                .model(setting.model())
                .temperature(setting.temperature());

        if (AiProviderSupport.usesThinkingToggle(setting.providerKind())) {
            options.extraBody(Map.of("thinking", Map.of("type", "disabled")));
        }

        OpenAiChatModel model = OpenAiChatModel.builder()
                .options(options.build())
                .build();

        return ChatClient.builder(model)
                .defaultAdvisors(tokenUsageAdvisor.withSetting(setting))
                .build();
    }

    private void validate(SettingsService.AiRuntimeSetting setting) {
        if (setting.baseUrl().isBlank() || setting.apiKey().isBlank() || setting.model().isBlank()) {
            throw new IllegalStateException("请先在设置页配置 AI 地址、API Key 和模型");
        }
    }

    private String cacheKey(SettingsService.AiRuntimeSetting setting) {
        String raw = setting.id() + "\n"
                + setting.providerKind() + "\n"
                + AiProviderSupport.trimTrailingSlash(setting.baseUrl()) + "\n"
                + setting.apiKey() + "\n"
                + setting.model() + "\n"
                + setting.temperature();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(raw.hashCode());
        }
    }

}
