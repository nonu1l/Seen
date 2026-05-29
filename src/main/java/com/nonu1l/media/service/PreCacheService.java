package com.nonu1l.media.service;

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
    private static final long TTL_SUBJECT = 600;
    private static final long TTL_CHARS = 600;

    private final String base;
    private final RequestCacheUtil cache;
    private final ExecutorService executor = Executors.newFixedThreadPool(10);
    private final AtomicLong batchCounter = new AtomicLong(0);

    public PreCacheService(RequestCacheUtil cache,
                           @Value("${seen.bangumi-proxy:}") String bangumiProxy) {
        this.cache = cache;
        if (!bangumiProxy.isBlank()) {
            this.base = bangumiProxy + "/api";
        } else {
            this.base = "https://api.bgm.tv/v0";
        }
    }

    public void preCache(List<Long> ids) {
        long batchId = batchCounter.incrementAndGet();
        long t0 = System.nanoTime();
        List<CompletableFuture<Void>> futures = ids.stream()
            .map(id -> CompletableFuture.runAsync(() -> {
                if (batchId != batchCounter.get()) return;
                cache.cacheGet(base + "/subjects/" + id, TTL_SUBJECT);
                cache.cacheGet(base + "/subjects/" + id + "/characters", TTL_CHARS);
            }, executor))
            .toList();
        // 异步等待全部完成，输出总耗时
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
            .thenRun(() -> log.debug("preCache batch={} done {}ms", batchId, (System.nanoTime() - t0) / 1_000_000));
    }

    @PreDestroy
    void shutdown() { executor.shutdownNow(); }
}
