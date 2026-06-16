package com.nonu1l.media.model.dto;

/**
 * 设置页连接测试请求 DTO 集合。
 */
public final class SettingsTestRequests {

    private SettingsTestRequests() {
    }

    public record AiTestRequest(
            String baseUrl,
            String apiKey,
            String model,
            Double temperature,
            Boolean clearApiKey
    ) {
    }

    public record SearchTestRequest(String provider, String serperApiKey, String bangumiProxy, String query) {
    }

    public record BangumiTestRequest(String bangumiProxy) {
    }
}
