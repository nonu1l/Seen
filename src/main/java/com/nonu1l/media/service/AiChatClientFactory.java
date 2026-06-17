package com.nonu1l.media.service;

import com.nonu1l.media.config.TokenUsageAdvisor;
import com.nonu1l.media.service.thinking.ThinkingMode;
import com.nonu1l.media.service.thinking.ThinkingStrategyRegistry;
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

    private static final String CACHE_VERSION = "thinking-strategy-v2";

    private final SettingsService settingsService;
    private final TokenUsageAdvisor tokenUsageAdvisor;
    private final ThinkingStrategyRegistry thinkingStrategyRegistry;
    private final ConcurrentHashMap<String, ChatClient> cache = new ConcurrentHashMap<>();

    public AiChatClientFactory(SettingsService settingsService,
                               TokenUsageAdvisor tokenUsageAdvisor,
                               ThinkingStrategyRegistry thinkingStrategyRegistry) {
        this.settingsService = settingsService;
        this.tokenUsageAdvisor = tokenUsageAdvisor;
        this.thinkingStrategyRegistry = thinkingStrategyRegistry;
    }

    /**
     * 使用业务默认思考模式创建当前 AI 客户端，优先启用 provider 支持的推理能力。
     *
     * @return 当前运行时配置对应的 ChatClient
     */
    public ChatClient currentClient() {
        return currentClient(ThinkingMode.ENABLED);
    }

    /**
     * 使用指定思考模式创建或复用当前 AI 客户端。
     *
     * @param mode 本次调用希望使用的思考模式
     * @return 当前运行时配置和思考模式对应的 ChatClient
     */
    public ChatClient currentClient(ThinkingMode mode) {
        SettingsService.AiRuntimeSetting setting = settingsService.currentRuntimeSetting();
        validate(setting);
        Map<String, Object> extraBody = thinkingStrategyRegistry().extraBody(setting, mode);
        String cacheKey = cacheKey(setting, mode, extraBody);
        return cache.computeIfAbsent(cacheKey, ignored -> buildClient(setting, extraBody));
    }

    private ThinkingStrategyRegistry thinkingStrategyRegistry() {
        return thinkingStrategyRegistry != null
                ? thinkingStrategyRegistry
                : ThinkingStrategyRegistry.defaultRegistry();
    }

    private ChatClient buildClient(SettingsService.AiRuntimeSetting setting, Map<String, Object> extraBody) {
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder()
                .baseUrl(AiProviderSupport.trimTrailingSlash(setting.baseUrl()))
                .apiKey(setting.apiKey())
                .model(setting.model())
                .temperature(setting.temperature());

        if (extraBody != null && !extraBody.isEmpty()) {
            options.extraBody(extraBody);
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

    String cacheKey(SettingsService.AiRuntimeSetting setting, ThinkingMode mode, Map<String, Object> extraBody) {
        String raw = CACHE_VERSION + "\n"
                + setting.id() + "\n"
                + setting.providerKind() + "\n"
                + AiProviderSupport.trimTrailingSlash(setting.baseUrl()) + "\n"
                + setting.apiKey() + "\n"
                + setting.model() + "\n"
                + setting.temperature() + "\n"
                + mode + "\n"
                + (extraBody != null ? extraBody.toString() : "");
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
