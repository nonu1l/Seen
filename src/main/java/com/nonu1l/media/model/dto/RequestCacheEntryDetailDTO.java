package com.nonu1l.media.model.dto;

/**
 * 请求缓存详情 DTO。
 *
 * @param key 缓存 key
 * @param method HTTP 方法
 * @param url 请求地址
 * @param requestBody 请求体
 * @param responseBody 响应体
 * @param responseBytes 响应体 UTF-8 字节数
 * @param remainingSeconds 剩余有效期秒数
 * @param cachedSecondsAgo 已缓存秒数
 */
public record RequestCacheEntryDetailDTO(
        String key,
        String method,
        String url,
        String requestBody,
        String responseBody,
        long responseBytes,
        long remainingSeconds,
        long cachedSecondsAgo
) {
}
