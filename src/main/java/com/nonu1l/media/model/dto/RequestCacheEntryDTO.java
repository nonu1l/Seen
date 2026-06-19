package com.nonu1l.media.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 请求缓存条目 DTO，列表和详情接口共用。
 *
 * @param key 缓存 key
 * @param method HTTP 方法
 * @param url 请求地址
 * @param requestBodyPreview 请求体预览，仅列表接口返回
 * @param requestBody 请求体，仅详情接口返回
 * @param responseBody 响应体，仅详情接口返回
 * @param responseBytes 响应体 UTF-8 字节数
 * @param remainingSeconds 剩余有效期秒数
 * @param cachedSecondsAgo 已缓存秒数
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RequestCacheEntryDTO(
        String key,
        String method,
        String url,
        String requestBodyPreview,
        String requestBody,
        String responseBody,
        long responseBytes,
        long remainingSeconds,
        long cachedSecondsAgo
) {
}
