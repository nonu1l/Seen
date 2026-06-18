package com.nonu1l.media.service.search;

import com.nonu1l.media.config.ExternalEndpointProperties;
import com.nonu1l.media.model.dto.WebSearchItemDTO;
import com.nonu1l.media.model.dto.WebSearchToolResultDTO;
import com.nonu1l.media.service.SettingsService;
import com.nonu1l.media.service.WebSearchProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Serper.dev Google 搜索 API
 *
 * 官方参考请求格式
 *
 * OkHttpClient client = new OkHttpClient().newBuilder()
 *   .build();
 * MediaType mediaType = MediaType.parse("application/json");
 * RequestBody body = RequestBody.create(mediaType, "{\"q\":\"2026年热门欧美剧推荐\",\"gl\":\"cn\",\"hl\":\"zh-cn\"}");
 * Request request = new Request.Builder()
 *   .url("Serper search endpoint")
 *   .method("POST", body)
 *   .addHeader("X-API-KEY", "your-api-key")
 *   .addHeader("Content-Type", "application/json")
 *   .build();
 * Response response = client.newCall(request).execute();
 *
 *
 * 数据返回
 *
 *
 * {
 *     "searchParameters": {
 *         "q": "2026年热门欧美剧推荐",
 *         "gl": "cn",
 *         "hl": "zh-cn",
 *         "type": "search",
 *         "engine": "google"
 *     },
 *     "organic": [
 *         {
 *             "title": "剧荒救星！2026开年热门美剧大盘点，每一部都值得熬夜追 - 搜狐",
 *             "link": "https://www.sohu.com/a/981853751_122559664",
 *             "snippet": "最后推荐一部爱情剧，《柏捷顿家族:名门韵事》第四季，2026年1月15日在Netflix上线，延续了前几季的精致与浪漫，改编自茱莉亚昆恩的摄政浪漫史小说。",
 *             "date": "2026年1月30日",
 *             "position": 1
 *         }
 *     ],
 *     "credits": 1
 * }
 *
 *
 */
@Service
public class SerperSearchService implements WebSearchProvider {

    private static final Logger log = LoggerFactory.getLogger(SerperSearchService.class);
    private static final int MAX_RESULTS = 10;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final SettingsService settingsService;
    private final ExternalEndpointProperties endpointProperties;

    /**
     * 构造 Serper 搜索服务。
     *
     * @param builder RestTemplate 构造器
     * @param objectMapper JSON 映射工具
     * @param settingsService 设置读取服务
     */
    public SerperSearchService(RestTemplateBuilder builder,
                                ObjectMapper objectMapper,
                                SettingsService settingsService,
                                ExternalEndpointProperties endpointProperties) {
        this.restTemplate = builder
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = objectMapper;
        this.settingsService = settingsService;
        this.endpointProperties = endpointProperties;
    }

    @Override
    public String providerKey() {
        return "serper";
    }

    /**
     * @return API Key 已配置则返回 true，可用于 provider 路由判断
     */
    @Override
    public boolean isAvailable() {
        return settingsService.hasText(SettingsService.SERPER_API_KEY);
    }

    /**
     * 调用 Serper 搜索并返回给 AI 工具使用的诊断信息。
     *
     * @param query 搜索关键词
     * @return 包含结果或失败原因的结构化结果
     */
    public WebSearchToolResultDTO searchWithDiagnostics(String query) {
        List<WebSearchItemDTO> results = new ArrayList<>();
        String apiKey = settingsService.getString(SettingsService.SERPER_API_KEY);
        if (apiKey.isBlank()) {
            log.warn("Serper search skipped: no API key");
            return new WebSearchToolResultDTO(false, query, "serper", 0, results,
                    "Serper API key is missing", null);
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-API-KEY", apiKey);

            String body = objectMapper.writeValueAsString(Map.of("q", query, "gl", "cn", "hl", "zh-cn"));
            HttpEntity<String> request = new HttpEntity<>(body, headers);

            String json = restTemplate.postForObject(
                    endpointProperties.getSerperSearchUrl(), request, String.class);
            if (json == null || json.isBlank()) {
                return new WebSearchToolResultDTO(false, query, "serper", 0, results,
                        "Serper returned empty response", null);
            }

            JsonNode root = objectMapper.readTree(json);
            JsonNode organic = root.get("organic");
            if (organic == null || !organic.isArray()) {
                return new WebSearchToolResultDTO(false, query, "serper", 0, results,
                        "Serper response has no organic results", null);
            }

            for (JsonNode r : organic) {
                String title = r.has("title") ? r.get("title").asText() : null;
                String snippet = r.has("snippet") ? r.get("snippet").asText() : "";
                String link = r.has("link") ? r.get("link").asText() : null;
                if (title != null && link != null) {
                    results.add(new WebSearchItemDTO(title, snippet, link));
                    if (results.size() >= MAX_RESULTS) break;
                }
            }
            log.info("Serper '{}' returned {} results", query, results.size());
            log.debug("Serper results:\n{}", results.stream()
                .map(r -> String.format("  [%s] %s", r.title(), r.url()))
                .reduce("", (a, b) -> a + b + "\n"));
            return new WebSearchToolResultDTO(true, query, "serper", results.size(), results, null, null);
        } catch (Exception e) {
            log.warn("Serper search failed '{}': {}", query, e.getMessage());
            return new WebSearchToolResultDTO(false, query, "serper", 0, results,
                    "Serper search failed: " + e.getMessage(), null);
        }
    }

}
