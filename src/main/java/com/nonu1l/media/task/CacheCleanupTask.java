package com.nonu1l.media.task;

import com.nonu1l.media.repository.RequestCacheRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 周期性清理过期请求缓存，避免数据库膨胀。
 */
@Component
public class CacheCleanupTask {

    private final RequestCacheRepository repo;

    /**
     * 注入请求缓存仓储。
     *
     * @param repo 缓存记录仓储。
     */
    public CacheCleanupTask(RequestCacheRepository repo) {
        this.repo = repo;
    }

    /**
     * 每 30 秒删除过期缓存行（按过期时间早于当前时间）。
     * 使用事务保证批量清理的一致性。
     */
    @Scheduled(fixedRate = 30_000)
    @Transactional
    public void cleanExpired() {
        repo.deleteByExpireTimeBefore(Instant.now());
    }
}
