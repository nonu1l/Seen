package com.nonu1l.media.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class PreCacheService {

    private static final Logger log = LoggerFactory.getLogger(PreCacheService.class);
    private static final String BASE = "https://api.bgm.tv/v0";
    private static final long TTL_SUBJECT = 600;    // 10 分钟
    private static final long TTL_CHARS = 600;      // 10 分钟

    private final RequestCacheUtil cache;
    private final ExecutorService executor = Executors.newFixedThreadPool(10);
    private final AtomicLong batchCounter = new AtomicLong(0);

    public PreCacheService(RequestCacheUtil cache) {
        this.cache = cache;
    }

    public void preCache(List<Long> ids) {
        long batchId = batchCounter.incrementAndGet();
        long t0 = System.nanoTime();
        List<CompletableFuture<Void>> futures = ids.stream()
            .map(id -> CompletableFuture.runAsync(() -> {
                if (batchId != batchCounter.get()) return;
                cache.cacheGet(BASE + "/subjects/" + id, TTL_SUBJECT);
                cache.cacheGet(BASE + "/subjects/" + id + "/characters", TTL_CHARS);
            }, executor))
            .toList();
        // 异步等待全部完成，输出总耗时
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
            .thenRun(() -> log.debug("preCache batch={} done {}ms", batchId, (System.nanoTime() - t0) / 1_000_000));
    }

    @PreDestroy
    void shutdown() { executor.shutdownNow(); }
}
