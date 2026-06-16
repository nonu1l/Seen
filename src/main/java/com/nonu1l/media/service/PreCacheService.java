package com.nonu1l.media.service;

import com.nonu1l.media.util.CachedHttpClient;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final CachedHttpClient httpClient;
    private final SettingsService settingsService;
    private final ExecutorService executor = Executors.newFixedThreadPool(10);
    private final AtomicLong batchCounter = new AtomicLong(0);

    /**
     * @param httpClient 带缓存的 HTTP 客户端
     * @param settingsService 设置读取服务
     */
    public PreCacheService(CachedHttpClient httpClient,
                           SettingsService settingsService) {
        this.httpClient = httpClient;
        this.settingsService = settingsService;
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
                httpClient.get(base + "/subjects/" + id, TTL_SUBJECT);
                if (castEnabled) {
                    httpClient.get(base + "/subjects/" + id + "/characters", TTL_CHARS);
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
