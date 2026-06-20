package com.nonu1l.media.service;

import com.nonu1l.media.util.CachedHttpClient;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 预缓存，用于搜索时提前加载搜索结果
 * @author nonu1l
 * @date 2026/05/29
 */
@Service
public class PreCacheService {

    private static final Logger log = LoggerFactory.getLogger(PreCacheService.class);

    private final CachedHttpClient httpClient;
    private final SettingsService settingsService;
    private final long subjectTtlSeconds;
    private final long charactersTtlSeconds;
    private final ExecutorService executor = Executors.newFixedThreadPool(10);
    private final AtomicLong batchCounter = new AtomicLong(0);

    /**
     * @param httpClient 带缓存的 HTTP 客户端
     * @param settingsService 设置读取服务
     * @param subjectTtlSeconds 作品详情预缓存 TTL
     * @param charactersTtlSeconds 角色页预缓存 TTL
     */
    public PreCacheService(CachedHttpClient httpClient,
                           SettingsService settingsService,
                           @Value("${app.runtime.cache.pre-cache-subject-ttl-seconds:600}") long subjectTtlSeconds,
                           @Value("${app.runtime.cache.pre-cache-characters-ttl-seconds:600}") long charactersTtlSeconds) {
        this.httpClient = httpClient;
        this.settingsService = settingsService;
        this.subjectTtlSeconds = subjectTtlSeconds;
        this.charactersTtlSeconds = charactersTtlSeconds;
    }

    /**
     * 异步预请求 Bangumi 作品详情，并按设置决定是否预取角色页缓存。
     *
     * <p>按固定线程池并行拉取，避免阻塞主请求线程；重复触发会覆盖旧 batchId。</p>
     *
     * @param ids 条目ID列表
     */
    public void preCache(List<Long> ids) {
        long batchId = batchCounter.incrementAndGet();
        long t0 = System.nanoTime();
        String base = settingsService.bangumiApiBase();
        boolean castEnabled = settingsService.getBoolean(SettingsService.DETAIL_CAST_ENABLED);
        List<CompletableFuture<Void>> futures = ids.stream()
            .map(id -> CompletableFuture.runAsync(() -> {
                if (batchId != batchCounter.get()) return;
                httpClient.get(base + "/subjects/" + id,
                        Math.max(1, subjectTtlSeconds));
                if (castEnabled) {
                    httpClient.get(base + "/subjects/" + id + "/characters",
                            Math.max(1, charactersTtlSeconds));
                }
            }, executor))
            .toList();
        // 异步等待全部完成，输出总耗时
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
            .thenRun(() -> log.debug("preCache batch={} done {}ms", batchId, (System.nanoTime() - t0) / 1_000_000));
    }

    /**
     * 释放线程池资源，避免应用退出时线程泄漏。
     */
    @PreDestroy
    void shutdown() { executor.shutdownNow(); }
}
