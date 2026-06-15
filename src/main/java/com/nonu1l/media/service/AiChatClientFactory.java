package com.nonu1l.media.service;

import com.nonu1l.media.config.TokenUsageAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 根据当前设置动态创建和复用 AI ChatClient。
 */
@Service
public class AiChatClientFactory {

    private final SettingsService settingsService;
    private final TokenUsageAdvisor tokenUsageAdvisor;
    private final ObjectProvider<RestClientCustomizer> restClientCustomizers;
    private final ObjectProvider<ToolCallingManager> toolCallingManagers;
    private final ConcurrentHashMap<String, ChatClient> cache = new ConcurrentHashMap<>();

    public AiChatClientFactory(SettingsService settingsService,
                               TokenUsageAdvisor tokenUsageAdvisor,
                               ObjectProvider<RestClientCustomizer> restClientCustomizers,
                               ObjectProvider<ToolCallingManager> toolCallingManagers) {
        this.settingsService = settingsService;
        this.tokenUsageAdvisor = tokenUsageAdvisor;
        this.restClientCustomizers = restClientCustomizers;
        this.toolCallingManagers = toolCallingManagers;
    }

    public ChatClient currentClient() {
        SettingsService.AiRuntimeSettings settings = settingsService.getAiRuntimeSettings();
        String cacheKey = cacheKey(settings);
        return cache.computeIfAbsent(cacheKey, ignored -> buildClient(settings));
    }

    private ChatClient buildClient(SettingsService.AiRuntimeSettings settings) {
        RestClient.Builder restClientBuilder = RestClient.builder();
        restClientCustomizers.orderedStream().forEach(customizer -> customizer.customize(restClientBuilder));

        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(trimTrailingSlash(settings.baseUrl()))
                .apiKey(settings.apiKey())
                .completionsPath(normalizePath(settings.completionsPath()))
                .restClientBuilder(restClientBuilder)
                .webClientBuilder(WebClient.builder())
                .responseErrorHandler(RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER)
                .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(settings.model())
                .temperature(settings.temperature())
                .build();

        OpenAiChatModel model = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .toolCallingManager(toolCallingManagers.getIfAvailable(ToolCallingManager.builder()::build))
                .retryTemplate(RetryUtils.DEFAULT_RETRY_TEMPLATE)
                .build();

        return ChatClient.builder(model)
                .defaultAdvisors(tokenUsageAdvisor)
                .build();
    }

    private String cacheKey(SettingsService.AiRuntimeSettings settings) {
        String raw = trimTrailingSlash(settings.baseUrl()) + "\n"
                + settings.apiKey() + "\n"
                + normalizePath(settings.completionsPath()) + "\n"
                + settings.model() + "\n"
                + settings.temperature();
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

    private static String trimTrailingSlash(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static String normalizePath(String value) {
        String result = value == null || value.isBlank() ? "/v1/chat/completions" : value.trim();
        return result.startsWith("/") ? result : "/" + result;
    }
}
