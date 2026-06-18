package com.nonu1l.media.model.dto;

/**
 * 设置页读取/保存后的结构化 DTO。
 */
public record SettingsDTO(
        boolean aiEnabled,
        boolean tokenUsageEnabled,
        AiMemorySettings aiMemory,
        AiProviderSettingDTO aiProfile,
        SourceSettings sources
) {

    /**
     * AI 长期记忆设置。
     *
     * @param enabled 是否启用长期记忆
     * @param autoUpdateEnabled 是否在记录变更后自动更新长期记忆
     */
    public record AiMemorySettings(
            boolean enabled,
            boolean autoUpdateEnabled
    ) {
    }

    public record SourceSettings(
            String searchProvider,
            boolean serperApiKeySet,
            String serperApiKey,
            boolean tavilyApiKeySet,
            String tavilyApiKey,
            String bangumiProxy,
            boolean detailCastEnabled
    ) {
    }
}
