package com.nonu1l.media.model.dto;

/**
 * 请求缓存列表项 DTO。
 *
 * @param key 缓存 key
 * @param method HTTP 方法
 * @param url 请求地址
 * @param requestBodyPreview 请求体预览
 * @param responseBytes 响应体 UTF-8 字节数
 * @param remainingSeconds 剩余有效期秒数
 * @param cachedSecondsAgo 已缓存秒数
 */
public record RequestCacheEntrySummaryDTO(
        String key,
        String method,
        String url,
        String requestBodyPreview,
        long responseBytes,
        long remainingSeconds,
        long cachedSecondsAgo
) {
}
