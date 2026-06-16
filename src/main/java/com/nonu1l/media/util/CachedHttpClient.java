package com.nonu1l.media.util;

import com.nonu1l.media.cache.CaffeineRequestCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * 带 Caffeine 注解缓存的 HTTP 客户端。
 */
@Component
public class CachedHttpClient {

    private static final Logger log = LoggerFactory.getLogger(CachedHttpClient.class);

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 发起 GET 请求，成功返回响应正文。
     *
     * @param url 请求地址
     * @param ttlSeconds 缓存有效期，单位秒
     * @return 响应正文；失败或非 2xx 返回 null
     */
    @CaffeineRequestCache(key = "T(com.nonu1l.media.cache.RequestCacheKeys).get(#url)", ttlSeconds = "#ttlSeconds")
    public String get(String url, long ttlSeconds) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "seen-app/1.0");
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            return response.getStatusCode().is2xxSuccessful() ? response.getBody() : null;
        } catch (Exception e) {
            log.error("cacheGet failed url={}: {}", url, e.getMessage());
            return null;
        }
    }

    /**
     * 发起 POST JSON 请求，成功返回响应正文。
     *
     * @param url 请求地址
     * @param body 请求体
     * @param ttlSeconds 缓存有效期，单位秒
     * @return 响应正文；失败或非 2xx 返回 null
     */
    @CaffeineRequestCache(key = "T(com.nonu1l.media.cache.RequestCacheKeys).post(#url, #body)", ttlSeconds = "#ttlSeconds")
    public String post(String url, String body, long ttlSeconds) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("User-Agent", "seen-app/1.0");
            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            return response.getStatusCode().is2xxSuccessful() ? response.getBody() : null;
        } catch (Exception e) {
            log.error("cachePost failed url={}: {}", url, e.getMessage());
            return null;
        }
    }
}
