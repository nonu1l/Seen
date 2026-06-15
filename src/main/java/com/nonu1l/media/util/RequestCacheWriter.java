package com.nonu1l.media.util;

import com.nonu1l.media.model.entity.RequestCache;
import com.nonu1l.media.repository.RequestCacheRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 异步持久化 HTTP 请求缓存。
 */
@Service
public class RequestCacheWriter {

    private static final Logger log = LoggerFactory.getLogger(RequestCacheWriter.class);

    private final RequestCacheRepository repo;

    public RequestCacheWriter(RequestCacheRepository repo) {
        this.repo = repo;
    }

    @Async
    @Transactional
    public void save(String url, String requestBody, String responseBody, long ttlSeconds) {
        String bodyKey = requestBody != null ? requestBody : "";
        try {
            RequestCache c = repo.findFirstByUrlAndRequestBody(url, bodyKey)
                    .orElseGet(RequestCache::new);
            c.setUrl(url);
            c.setRequestBody(bodyKey);
            c.setResponseBody(responseBody);
            c.setExpireTime(Instant.now().plusSeconds(ttlSeconds));
            repo.saveAndFlush(c);
            log.debug("cache saved url={} ttl={}s", url, ttlSeconds);
        } catch (Exception e) {
            log.debug("cache save skipped url={}: {}", url, e.getMessage());
        }
    }
}
