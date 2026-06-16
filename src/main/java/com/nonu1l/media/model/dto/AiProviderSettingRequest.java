package com.nonu1l.media.model.dto;

/**
 * 单一 AI 接入配置保存请求。
 */
public record AiProviderSettingRequest(
        String baseUrl,
        String model,
        Double temperature,
        String apiKey,
        Boolean clearApiKey
) {
}
