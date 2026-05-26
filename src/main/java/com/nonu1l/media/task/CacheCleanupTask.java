package com.nonu1l.media.task;

import com.nonu1l.media.repository.RequestCacheRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class CacheCleanupTask {

    private final RequestCacheRepository repo;

    public CacheCleanupTask(RequestCacheRepository repo) {
        this.repo = repo;
    }

    @Scheduled(fixedRate = 30_000)
    @Transactional
    public void cleanExpired() {
        repo.deleteByExpireTimeBefore(Instant.now());
    }
}
