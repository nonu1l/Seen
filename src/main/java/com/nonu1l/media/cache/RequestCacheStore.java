package com.nonu1l.media.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Caffeine 的进程内 HTTP 请求缓存。
 */
@Component
public class RequestCacheStore {

    private static final long MAXIMUM_SIZE = 5000;

    private final Cache<String, CachedResponse> cache = Caffeine.newBuilder()
            .maximumSize(MAXIMUM_SIZE)
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
        cache.put(key, new CachedResponse(body, System.nanoTime() + ttlNanos));
    }

    private long remainingNanos(CachedResponse value, long currentTime) {
        return Math.max(0, value.expireAtNanos() - currentTime);
    }
}
