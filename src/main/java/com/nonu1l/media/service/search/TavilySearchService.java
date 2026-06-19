package com.nonu1l.media.service.search;

import com.nonu1l.media.config.ExternalEndpointProperties;
import com.nonu1l.media.model.dto.WebSearchItemDTO;
import com.nonu1l.media.model.dto.WebSearchResultDTO;
import com.nonu1l.media.service.SettingsService;
import com.nonu1l.media.service.WebSearchProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tavily 搜索 API provider，负责把 Tavily 响应映射为统一 Web 搜索结果。
 */
@Service
public class TavilySearchService implements WebSearchProvider {

    private static final Logger log = LoggerFactory.getLogger(TavilySearchService.class);
    private static final int MAX_RESULTS = 10;
    private static final int MAX_ATTEMPTS = 3;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final SettingsService settingsService;
    private final ExternalEndpointProperties endpointProperties;

    /**
     * 构造 Tavily 搜索服务。
     *
     * @param builder RestTemplate 构造器
     * @param objectMapper JSON 映射工具
     * @param settingsService 设置读取服务
     * @param endpointProperties 外部 endpoint 配置
     */
    public TavilySearchService(RestTemplateBuilder builder,
                               ObjectMapper objectMapper,
                               SettingsService settingsService,
                               ExternalEndpointProperties endpointProperties) {
        this.restTemplate = builder
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(20))
                .build();
        this.objectMapper = objectMapper;
        this.settingsService = settingsService;
        this.endpointProperties = endpointProperties;
    }

    @Override
    public String providerKey() {
        return "tavily";
    }

    /**
     * @return Tavily API Key 已配置则返回 true。
     */
    @Override
    public boolean isAvailable() {
        return settingsService.hasText(SettingsService.TAVILY_API_KEY);
    }

    /**
     * 使用 Tavily Search API 检索网页，并保留失败原因给 Agent。
     *
     * @param query 搜索关键词
     * @return 统一搜索诊断结果
     */
    @Override
    public WebSearchResultDTO searchWithDiagnostics(String query) {
        List<WebSearchItemDTO> results = new ArrayList<>();
        String apiKey = settingsService.getString(SettingsService.TAVILY_API_KEY);
        if (apiKey.isBlank()) {
            log.warn("Tavily search skipped: no API key");
            return new WebSearchResultDTO(false, query, providerKey(), 0, results,
                    "Tavily API key is missing", null);
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("query", query);
            body.put("search_depth", "basic");
            body.put("max_results", MAX_RESULTS);
            body.put("include_answer", false);
            body.put("include_raw_content", false);
            body.put("include_images", false);

            JsonNode root = null;
            for (int attempt = 1; attempt < MAX_ATTEMPTS; attempt++) {
                try {
                    root = requestTavily(body, headers);
                    break;
                } catch (Exception e) {
                    log.warn("Tavily search failed '{}' (attempt {}/{}): {}",
                            query, attempt, MAX_ATTEMPTS, e.getMessage());
                }
            }
            if (root == null) {
                try {
                    root = requestTavily(body, headers);
                } catch (Exception e) {
                    log.warn("Tavily search failed '{}' (attempt {}/{}): {}",
                            query, MAX_ATTEMPTS, MAX_ATTEMPTS, e.getMessage());
                    return new WebSearchResultDTO(false, query, providerKey(), 0, results,
                            "Tavily search failed: " + e.getMessage(), null);
                }
            }

            JsonNode items = root.get("results");
            if (items == null || !items.isArray()) {
                return new WebSearchResultDTO(false, query, providerKey(), 0, results,
                        "Tavily response has no results", null);
            }

            for (JsonNode item : items) {
                String title = item.has("title") ? item.get("title").asString() : null;
                String url = item.has("url") ? item.get("url").asString() : null;
                String content = item.has("content") ? item.get("content").asString() : "";
                if (title != null && !title.isBlank() && url != null && !url.isBlank()) {
                    results.add(new WebSearchItemDTO(title, content, url));
                    if (results.size() >= MAX_RESULTS) break;
                }
            }

            log.info("Tavily '{}' returned {} results", query, results.size());
            log.debug("Tavily results:\n{}", results.stream()
                    .map(r -> String.format("  [%s] %s", r.title(), r.url()))
                    .reduce("", (a, b) -> a + b + "\n"));
            return new WebSearchResultDTO(true, query, providerKey(), results.size(), results, null, null);
        } catch (Exception e) {
            log.warn("Tavily search failed '{}': {}", query, e.getMessage());
            return new WebSearchResultDTO(false, query, providerKey(), 0, results,
                    "Tavily search failed: " + e.getMessage(), null);
        }
    }

    /**
     * 执行一次 Tavily HTTP 请求并解析响应；调用方负责重试和失败返回。
     *
     * @param body Tavily Search API 请求体
     * @param headers 包含认证信息的请求头
     * @return Tavily 响应根节点
     * @throws Exception 网络、空响应或 JSON 解析失败时抛出
     */
    private JsonNode requestTavily(Map<String, Object> body, HttpHeaders headers) throws Exception {
        String json = restTemplate.postForObject(
                endpointProperties.getTavilySearchUrl(),
                new HttpEntity<>(objectMapper.writeValueAsString(body), headers),
                String.class
        );
        if (json == null || json.isBlank()) {
            throw new IllegalStateException("Tavily returned empty response");
        }
        return objectMapper.readTree(json);
    }
}
