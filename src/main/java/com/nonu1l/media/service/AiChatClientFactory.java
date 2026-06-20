package com.nonu1l.media.service;

import com.nonu1l.media.config.TokenUsageAdvisor;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 根据设置页保存的 Anthropic-compatible AI 接入配置动态创建和复用 ChatClient。
 */
@Service
public class AiChatClientFactory {

    private static final String CACHE_VERSION = "anthropic-client-v1";
    private static final int DEFAULT_MAX_TOKENS = 4096;
    private static final int THINKING_MAX_TOKENS = 8192;
    private static final int THINKING_BUDGET_TOKENS = 2048;

    private final SettingsService settingsService;
    private final TokenUsageAdvisor tokenUsageAdvisor;
    private final ConcurrentHashMap<String, ChatClient> cache = new ConcurrentHashMap<>();

    public AiChatClientFactory(SettingsService settingsService,
                               TokenUsageAdvisor tokenUsageAdvisor) {
        this.settingsService = settingsService;
        this.tokenUsageAdvisor = tokenUsageAdvisor;
    }

    /**
     * 使用设置页保存的默认思考模式创建当前 AI 客户端。
     *
     * @return 当前运行时配置对应的 ChatClient
     */
    public ChatClient currentClient() {
        return currentClient(settingsService.currentThinkingMode());
    }

    /**
     * 使用指定思考模式创建或复用当前 AI 客户端。
     *
     * @param mode 本次调用希望使用的思考模式
     * @return 当前运行时配置和思考模式对应的 ChatClient
     */
    public ChatClient currentClient(AiThinkingMode mode) {
        SettingsService.AiRuntimeSetting setting = settingsService.currentRuntimeSetting();
        return clientFor(setting, mode);
    }

    /**
     * 使用指定运行时配置创建或复用 Anthropic ChatClient，供设置页草稿测试使用。
     *
     * @param setting AI 运行时配置
     * @param mode 思考模式
     * @return 对应的 ChatClient
     */
    public ChatClient clientFor(SettingsService.AiRuntimeSetting setting, AiThinkingMode mode) {
        validate(setting);
        AiThinkingMode effectiveMode = mode != null && mode != AiThinkingMode.DEFAULT
                ? mode
                : AiThinkingMode.DISABLED;
        String cacheKey = cacheKey(setting, effectiveMode);
        return cache.computeIfAbsent(cacheKey, ignored -> buildClient(setting, effectiveMode));
    }

    /**
     * Anthropic-compatible 主链路下正文由 text block 产生，不再清理 provider 私有 thinking 标签。
     *
     * @param content 模型原始正文
     * @return 原样正文
     */
    public String cleanAssistantContent(String content) {
        return content;
    }

    private ChatClient buildClient(SettingsService.AiRuntimeSetting setting, AiThinkingMode mode) {
        AnthropicChatOptions.Builder options = AnthropicChatOptions.builder()
                .baseUrl(trimTrailingSlash(setting.baseUrl()))
                .apiKey(setting.apiKey())
                .model(setting.model())
                .temperature(mode == AiThinkingMode.ENABLED ? 1.0 : setting.temperature())
                .maxTokens(mode == AiThinkingMode.ENABLED ? THINKING_MAX_TOKENS : DEFAULT_MAX_TOKENS);

        if (mode == AiThinkingMode.ENABLED) {
            options.thinkingEnabled(THINKING_BUDGET_TOKENS);
        }

        AnthropicChatModel model = AnthropicChatModel.builder()
                .options(options.build())
                .build();

        return ChatClient.builder(model)
                .defaultAdvisors(tokenUsageAdvisor.withSetting(setting))
                .build();
    }

    private static String trimTrailingSlash(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private void validate(SettingsService.AiRuntimeSetting setting) {
        if (setting.baseUrl().isBlank() || setting.apiKey().isBlank() || setting.model().isBlank()) {
            throw new IllegalStateException("请先在设置页配置 AI 地址、API Key 和模型");
        }
    }

    String cacheKey(SettingsService.AiRuntimeSetting setting, AiThinkingMode mode) {
        String raw = CACHE_VERSION + "\n"
                + setting.id() + "\n"
                + trimTrailingSlash(setting.baseUrl()) + "\n"
                + setting.apiKey() + "\n"
                + setting.model() + "\n"
                + setting.temperature() + "\n"
                + mode;
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
