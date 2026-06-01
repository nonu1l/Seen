package com.nonu1l.media.util;

import com.nonu1l.media.model.entity.RequestCache;
import com.nonu1l.media.repository.RequestCacheRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Optional;

@Service
public class RequestCacheUtil {

    private static final Logger log = LoggerFactory.getLogger(RequestCacheUtil.class);
    private static final long DEFAULT_TTL = 300;

    private final RequestCacheRepository repo;
    private final RestTemplate restTemplate;

    public RequestCacheUtil(RequestCacheRepository repo) {
        this.repo = repo;
        this.restTemplate = new RestTemplate();
    }

    public String cacheGet(String url) {
        return cacheGet(url, DEFAULT_TTL);
    }

    public String cacheGet(String url, long ttlSeconds) {
        long t0 = System.nanoTime();
        Optional<String> cached = findCache(url, "");
        if (cached.isPresent()) {
            log.debug("cacheGet HIT {}ms url={}", (System.nanoTime() - t0) / 1_000_000, url);
            return cached.get();
        }
        log.debug("cacheGet MISS {}ms url={}", (System.nanoTime() - t0) / 1_000_000, url);
        try {
            HttpHeaders h = new HttpHeaders();
            h.set("User-Agent", "seen-app/1.0");
            h.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
            HttpEntity<Void> entity = new HttpEntity<>(h);
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            if (!resp.getStatusCode().is2xxSuccessful()) return null;
            String body = resp.getBody();
            if (body != null) {
                asyncSave(url, "", body, ttlSeconds);
            }
            return body;
        } catch (Exception e) {
            log.error("cacheGet failed url={}: {}", url, e.getMessage());
            return null;
        }
    }

    public String cachePost(String url, String requestBody) {
        return cachePost(url, requestBody, DEFAULT_TTL);
    }

    public String cachePost(String url, String requestBody, long ttlSeconds) {
        long t0 = System.nanoTime();
        Optional<String> cached = findCache(url, requestBody);
        if (cached.isPresent()) {
            log.debug("cachePost HIT {}ms url={}", (System.nanoTime() - t0) / 1_000_000, url);
            return cached.get();
        }
        log.debug("cachePost MISS {}ms url={}", (System.nanoTime() - t0) / 1_000_000, url);
        try {
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_JSON);
            h.set("User-Agent", "seen-app/1.0");
            HttpEntity<String> entity = new HttpEntity<>(requestBody, h);
            ResponseEntity<String> resp = restTemplate.postForEntity(url, entity, String.class);
            if (!resp.getStatusCode().is2xxSuccessful()) return null;
            String body = resp.getBody();
            if (body != null) {
                asyncSave(url, requestBody, body, ttlSeconds);
            }
            return body;
        } catch (Exception e) {
            log.error("cachePost failed url={}: {}", url, e.getMessage());
            return null;
        }
    }

    private Optional<String> findCache(String url, String requestBody) {
        Optional<RequestCache> entry = repo.findCache(url, requestBody, Instant.now());
        return entry.map(RequestCache::getResponseBody);
    }

    @Async
    public void asyncSave(String url, String requestBody, String responseBody, long ttlSeconds) {
        try {
            RequestCache c = new RequestCache();
            c.setUrl(url);
            c.setRequestBody(requestBody);
            c.setResponseBody(responseBody);
            c.setExpireTime(Instant.now().plusSeconds(ttlSeconds));
            repo.save(c);
            log.debug("cache saved url={} ttl={}s", url, ttlSeconds);
        } catch (Exception e) {
            // 唯一约束冲突 → 已有其他线程写了，忽略
            log.debug("cache save skipped (duplicate?) url={}", url);
        }
    }
}
