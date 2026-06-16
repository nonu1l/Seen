package com.nonu1l.media.cache;

/**
 * 单条 HTTP 响应缓存值，过期时间使用 Caffeine ticker 的纳秒时间。
 *
 * @param body 响应正文
 * @param cachedAtNanos 写入缓存时间点
 * @param expireAtNanos 过期时间点
 */
public record CachedResponse(String body, long cachedAtNanos, long expireAtNanos) {
}
