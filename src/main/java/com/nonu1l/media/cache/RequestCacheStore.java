package com.nonu1l.media.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 基于 Caffeine 的进程内 HTTP 请求缓存。
 */
@Component
public class RequestCacheStore {

    private final Cache<String, CachedResponse> cache;

    /**
     * @param maximumSize 进程内请求缓存最大条目数
     */
    public RequestCacheStore(@Value("${app.runtime.cache.request-maximum-size:5000}") long maximumSize) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(Math.max(1, maximumSize))
                .expireAfter(new Expiry<String, CachedResponse>() {
                    @Override
                    public long expireAfterCreate(@NonNull String key, @NonNull CachedResponse value, long currentTime) {
                        return remainingNanos(value, currentTime);
                    }

                    @Override
                    public long expireAfterUpdate(@NonNull String key, @NonNull CachedResponse value,
                                                  long currentTime, long currentDuration) {
                        return remainingNanos(value, currentTime);
                    }

                    @Override
                    public long expireAfterRead(@NonNull String key, @NonNull CachedResponse value,
                                                long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                })
                .build();
    }

    /**
     * 按 key 读取未过期响应正文。
     *
     * @param key 缓存 key
     * @return 命中则返回响应正文
     */
    public Optional<String> get(String key) {
        CachedResponse response = cache.getIfPresent(key);
        return response != null ? Optional.of(response.body()) : Optional.empty();
    }

    /**
     * 写入响应正文，按本条记录 TTL 独立过期。
     *
     * @param key 缓存 key
     * @param body 响应正文
     * @param ttlSeconds TTL，单位秒
     */
    public void put(String key, String body, long ttlSeconds) {
        if (body == null || ttlSeconds <= 0) {
            return;
        }
        long ttlNanos = TimeUnit.SECONDS.toNanos(ttlSeconds);
        long now = System.nanoTime();
        cache.put(key, new CachedResponse(body, now, now + ttlNanos));
    }

    /**
     * 读取当前缓存快照。
     *
     * @return 按剩余时间升序排列的缓存条目
     */
    public List<SnapshotEntry> snapshot() {
        cache.cleanUp();
        long now = System.nanoTime();
        return cache.asMap().entrySet().stream()
                .map(entry -> snapshotEntry(entry.getKey(), entry.getValue(), now))
                .sorted(Comparator.comparingLong(SnapshotEntry::remainingSeconds)
                        .thenComparing(SnapshotEntry::method)
                        .thenComparing(SnapshotEntry::url))
                .collect(Collectors.toList());
    }

    /**
     * 统计当前进程内缓存响应体总字节数。
     *
     * @return 未过期缓存响应体 UTF-8 字节数之和
     */
    public long totalBytes() {
        return snapshot().stream()
                .mapToLong(SnapshotEntry::responseBytes)
                .sum();
    }

    /**
     * 清空当前进程内请求缓存。
     */
    public void clear() {
        cache.invalidateAll();
        cache.cleanUp();
    }

    /**
     * 查询单条缓存详情。
     *
     * @param key 缓存 key
     * @return 命中则返回缓存条目
     */
    public Optional<SnapshotEntry> find(String key) {
        cache.cleanUp();
        CachedResponse response = cache.getIfPresent(key);
        return response != null ? Optional.of(snapshotEntry(key, response, System.nanoTime())) : Optional.empty();
    }

    private long remainingNanos(CachedResponse value, long currentTime) {
        return Math.max(0, value.expireAtNanos() - currentTime);
    }

    private SnapshotEntry snapshotEntry(String key, CachedResponse response, long now) {
        RequestCacheKeys.ParsedKey parsed = RequestCacheKeys.parse(key);
        long remainingSeconds = TimeUnit.NANOSECONDS.toSeconds(Math.max(0, response.expireAtNanos() - now));
        long cachedSecondsAgo = TimeUnit.NANOSECONDS.toSeconds(Math.max(0, now - response.cachedAtNanos()));
        long responseBytes = response.body().getBytes(StandardCharsets.UTF_8).length;
        return new SnapshotEntry(key, parsed.method(), parsed.url(), parsed.body(), response.body(),
                responseBytes, remainingSeconds, cachedSecondsAgo);
    }

    /**
     * 缓存快照条目。
     *
     * @param key 缓存 key
     * @param method HTTP 方法
     * @param url 请求地址
     * @param requestBody 请求体
     * @param responseBody 响应体
     * @param responseBytes 响应体字节数
     * @param remainingSeconds 剩余有效期秒数
     * @param cachedSecondsAgo 已缓存秒数
     */
    public record SnapshotEntry(
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
}
