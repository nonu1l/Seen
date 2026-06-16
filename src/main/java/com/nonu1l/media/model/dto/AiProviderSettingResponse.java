package com.nonu1l.media.model.dto;

/**
 * 单一 AI 接入配置响应。设置页按明文返回 API Key，由前端输入框控制显示/隐藏。
 */
public record AiProviderSettingResponse(
        String baseUrl,
        String model,
        Double temperature,
        boolean apiKeySet,
        String apiKey
) {
}
