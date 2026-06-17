package com.nonu1l.media.controller;

import com.nonu1l.media.cache.RequestCacheStore;
import com.nonu1l.media.model.dto.RequestCacheEntryDetailDTO;
import com.nonu1l.media.model.dto.RequestCacheEntrySummaryDTO;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;

/**
 * 后台管理接口：展示 Caffeine 请求缓存页面并提供缓存查询 API。
 */



/**
 * TODO 这个可以合并到 TokenUsageController 统一对外的调用
 */
@RestController
public class RequestCacheController {

    private final RequestCacheStore cacheStore;

    /**
     * @param cacheStore 请求缓存存储
     */
    public RequestCacheController(RequestCacheStore cacheStore) {
        this.cacheStore = cacheStore;
    }

    /**
     * 返回请求缓存管理 HTML 页面。
     *
     * @return 页面 HTML 文本
     * @throws Exception 读取模板文件失败时抛出
     */
    @GetMapping(value = "/admin/request-cache", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String page() throws Exception {
        return new String(new ClassPathResource("admin-pages/request-cache.html")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    /**
     * 查询当前进程内请求缓存列表。
     *
     * @return 缓存列表摘要，按 HTTP 方法和 URL 排序
     */
    @GetMapping("/api/admin/request-cache/entries")
    public List<RequestCacheEntrySummaryDTO> entries() {
        return cacheStore.snapshot().stream()
                .sorted(Comparator.comparing(RequestCacheStore.SnapshotEntry::method)
                        .thenComparing(RequestCacheStore.SnapshotEntry::url))
                .map(this::toSummary)
                .toList();
    }

    /**
     * 查询单条缓存详情。
     *
     * @param key 缓存 key
     * @return 缓存详情；未命中返回空详情
     */
    @GetMapping("/api/admin/request-cache/detail")
    public RequestCacheEntryDetailDTO detail(@RequestParam String key) {
        return cacheStore.find(key)
                .map(this::toDetail)
                .orElseGet(() -> new RequestCacheEntryDetailDTO(key, "MISS", "", "", "",
                        0L, 0L, 0L));
    }

    private RequestCacheEntrySummaryDTO toSummary(RequestCacheStore.SnapshotEntry entry) {
        return new RequestCacheEntrySummaryDTO(
                entry.key(),
                entry.method(),
                entry.url(),
                preview(entry.requestBody(), 120),
                entry.responseBytes(),
                entry.remainingSeconds(),
                entry.cachedSecondsAgo()
        );
    }

    private RequestCacheEntryDetailDTO toDetail(RequestCacheStore.SnapshotEntry entry) {
        return new RequestCacheEntryDetailDTO(
                entry.key(),
                entry.method(),
                entry.url(),
                entry.requestBody(),
                entry.responseBody(),
                entry.responseBytes(),
                entry.remainingSeconds(),
                entry.cachedSecondsAgo()
        );
    }

    private String preview(String text, int maxLength) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "...";
    }
}
