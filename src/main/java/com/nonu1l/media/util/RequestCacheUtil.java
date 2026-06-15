package com.nonu1l.media.util;

import com.nonu1l.media.model.entity.RequestCache;
import com.nonu1l.media.repository.RequestCacheRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Optional;

/**
 * 基于数据库的 HTTP 请求缓存工具。
 * <p>支持 GET/POST 的幂等请求缓存读取与异步持久化，减少重复网络请求。</p>
 */
@Service
public class RequestCacheUtil {

    private static final Logger log = LoggerFactory.getLogger(RequestCacheUtil.class);
    private static final long DEFAULT_TTL = 300;

    private final RequestCacheRepository repo;
    private final RequestCacheWriter writer;
    private final RestTemplate restTemplate;

    /**
     * 初始化缓存工具及其默认 RestTemplate 与仓储依赖。
     *
     * @param repo 请求缓存仓储。
     * @param writer 异步缓存写入器。
     */
    public RequestCacheUtil(RequestCacheRepository repo, RequestCacheWriter writer) {
        this.repo = repo;
        this.writer = writer;
        this.restTemplate = new RestTemplate();
    }

    /**
     * 使用默认 TTL（300 秒）读取 GET 缓存。
     *
     * @param url 请求地址。
     * @return 命中返回缓存体；未命中则发起网络请求并在成功后异步写入缓存。
     */
    public String cacheGet(String url) {
        return cacheGet(url, DEFAULT_TTL);
    }

    /**
     * 使用指定 TTL 读取 GET 缓存。
     *
     * @param url 请求地址。
     * @param ttlSeconds 缓存有效期（秒）。
     * @return 请求响应文本；失败或非 2xx 返回 null。
     */
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
                writer.save(url, "", body, ttlSeconds);
            }
            return body;
        } catch (Exception e) {
            log.error("cacheGet failed url={}: {}", url, e.getMessage());
            return null;
        }
    }

    /**
     * 使用默认 TTL（300 秒）读取 POST 缓存。
     *
     * @param url         请求地址。
     * @param requestBody 请求体（用于区分同名 URL 的不同参数）。
     * @return 请求响应文本；失败或非 2xx 返回 null。
     */
    public String cachePost(String url, String requestBody) {
        return cachePost(url, requestBody, DEFAULT_TTL);
    }

    /**
     * 使用指定 TTL 读取 POST 缓存。
     *
     * @param url         请求地址。
     * @param requestBody 请求体原文。
     * @param ttlSeconds 缓存有效期（秒）。
     * @return 命中则返回缓存，否则发起请求并异步存储结果；失败返回 null。
     */
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
                writer.save(url, requestBody, body, ttlSeconds);
            }
            return body;
        } catch (Exception e) {
            log.error("cachePost failed url={}: {}", url, e.getMessage());
            return null;
        }
    }

    private Optional<String> findCache(String url, String requestBody) {
        String bodyKey = requestBody != null ? requestBody : "";
        Optional<RequestCache> entry = repo.findCache(url, bodyKey, Instant.now());
        return entry.map(RequestCache::getResponseBody);
    }
}
