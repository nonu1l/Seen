package com.nonu1l.media.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nonu1l.media.model.dto.WebSearchItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Web 搜索 — 通过自部署 SearXNG 聚合搜索引擎结果。
 */
@Service
public class WebSearchService {

    private static final Logger log = LoggerFactory.getLogger(WebSearchService.class);
    private static final int MAX_RESULTS = 10;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String searchBase;

    public WebSearchService(RestTemplateBuilder builder,
                            ObjectMapper objectMapper,
                            @Value("${seen.search-url}") String searchBase) {
        this.restTemplate = builder
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = objectMapper;
        this.searchBase = searchBase;
    }

    public List<WebSearchItem> search(String query) {
        List<WebSearchItem> results = new ArrayList<>();
        try {
            String url = searchBase + "/search";
            String body = "format=json&q="
                    + java.net.URLEncoder.encode(query, StandardCharsets.UTF_8)
                            .replace("+", "%20");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            HttpEntity<String> request = new HttpEntity<>(body, headers);

            String json = restTemplate.postForObject(url, request, String.class);
            if (json == null) return results;

            JsonNode root = objectMapper.readTree(json);
            JsonNode list = root.get("results");
            if (list == null || !list.isArray()) return results;

            for (JsonNode r : list) {
                String title = text(r, "title");
                String snippet = text(r, "content");
                String link = text(r, "url");
                if (title != null && link != null) {
                    results.add(new WebSearchItem(title,
                            snippet != null ? snippet : "",
                            link));
                    if (results.size() >= MAX_RESULTS) break;
                }
            }
            log.info("WebSearch '{}' returned {} results", query, results.size());
        } catch (Exception e) {
            log.warn("WebSearch failed '{}': {}", query, e.getMessage());
        }
        return results;
    }

    private String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v != null && !v.isNull() ? v.asText() : null;
    }

    /** 抓取网页纯文本（最多 3000 字符），供 LLM 阅读搜索结果详情 */
    public String fetch(String url) {
        try {
            String html = restTemplate.getForObject(url, String.class);
            if (html == null) return null;
            String text = html
                    .replaceAll("(?s)<script[^>]*>.*?</script>", " ")
                    .replaceAll("(?s)<style[^>]*>.*?</style>", " ")
                    .replaceAll("<[^>]+>", " ")
                    .replaceAll("&amp;", "&").replaceAll("&lt;", "<")
                    .replaceAll("&gt;", ">").replaceAll("&quot;", "\"")
                    .replaceAll("&#\\d+;", " ")
                    .replaceAll("\\s+", " ")
                    .trim();
            log.info("WebFetch {} chars from {}", text.length(), url);
            return text.length() <= 3000 ? text : text.substring(0, 3000);
        } catch (Exception e) {
            log.warn("WebFetch failed '{}': {}", url, e.getMessage());
            return null;
        }
    }
}
