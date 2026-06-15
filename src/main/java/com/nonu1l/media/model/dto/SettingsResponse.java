package com.nonu1l.media.model.dto;

/**
 * 设置页读取/保存后的结构化响应。
 */
public record SettingsResponse(
        boolean aiEnabled,
        boolean tokenUsageEnabled,
        AiProviderSettingResponse aiProfile,
        SourceSettings sources
) {

    public record SourceSettings(
            String searchProvider,
            boolean serperApiKeySet,
            String serperApiKey,
            String bangumiProxy,
            boolean detailCastEnabled
    ) {
    }
}
