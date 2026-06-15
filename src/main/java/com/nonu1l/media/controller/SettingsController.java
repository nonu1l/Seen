package com.nonu1l.media.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nonu1l.media.model.dto.SettingsResponse;
import com.nonu1l.media.model.dto.SettingsTestRequests;
import com.nonu1l.media.model.dto.SettingsTestResponse;
import com.nonu1l.media.model.dto.UpdateSettingsRequest;
import com.nonu1l.media.service.SettingsService;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * 设置页后端接口，负责读取、保存和连接测试。
 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private static final String TEST_QUERY = "孤独摇滚";

    private final SettingsService settingsService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public SettingsController(SettingsService settingsService,
                              RestTemplateBuilder builder,
                              ObjectMapper objectMapper) {
        this.settingsService = settingsService;
        this.restTemplate = builder
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(20))
                .build();
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public SettingsResponse getSettings() {
        return settingsService.getSettingsResponse();
    }

    @PutMapping
    public SettingsResponse updateSettings(@RequestBody UpdateSettingsRequest request) {
        return settingsService.updateSettings(request != null ? request.settings() : Map.of());
    }

    @PostMapping("/test-ai")
    public SettingsTestResponse testAi(@RequestBody SettingsTestRequests.AiTestRequest request) {
        long start = System.nanoTime();
        String apiKey = valueOrCurrent(request != null ? request.apiKey() : null, SettingsService.OPENAI_API_KEY);
        String baseUrl = valueOrCurrent(request != null ? request.baseUrl() : null, SettingsService.OPENAI_BASE_URL);
        String model = valueOrCurrent(request != null ? request.model() : null, SettingsService.OPENAI_MODEL);
        Double temperature = request != null && request.temperature() != null ? request.temperature() : 0.0d;
        if (baseUrl.isBlank() || apiKey.isBlank() || model.isBlank()) {
            return response(false, "baseUrl、apiKey、model 不能为空", start, Map.of("model", model));
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("temperature", Math.max(0.0d, Math.min(2.0d, temperature)));
            body.put("max_tokens", 8);
            body.put("messages", List.of(Map.of("role", "user", "content", "ping")));

            ResponseEntity<String> resp = restTemplate.exchange(
                    trimTrailingSlash(baseUrl) + "/chat/completions",
                    HttpMethod.POST,
                    new HttpEntity<>(objectMapper.writeValueAsString(body), headers),
                    String.class
            );
            boolean ok = resp.getStatusCode().is2xxSuccessful();
            return response(ok, ok ? "连接正常" : "连接失败", start, Map.of("model", model));
        } catch (Exception e) {
            return response(false, sanitize(e.getMessage(), apiKey), start, Map.of("model", model));
        }
    }

    @PostMapping("/test-search")
    public SettingsTestResponse testSearch(@RequestBody SettingsTestRequests.SearchTestRequest request) {
        long start = System.nanoTime();
        String provider = request != null ? blankToEmpty(request.provider()) : "ddg";
        String serperApiKey = valueOrCurrent(request != null ? request.serperApiKey() : null, SettingsService.SERPER_API_KEY);
        String bangumiProxy = valueOrCurrent(request != null ? request.bangumiProxy() : null, SettingsService.BANGUMI_PROXY);
        String query = request != null && request.query() != null && !request.query().isBlank()
                ? request.query().trim()
                : TEST_QUERY;
        try {
            List<String> titles = "serper".equalsIgnoreCase(provider)
                    ? testSerperSearch(query, serperApiKey)
                    : testDdgSearch(query, bangumiProxy);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("provider", "serper".equalsIgnoreCase(provider) ? "serper" : "ddg");
            details.put("count", titles.size());
            details.put("titles", titles.stream().limit(5).toList());
            return response(true, "搜索连接正常", start, details);
        } catch (Exception e) {
            return response(false, sanitize(e.getMessage(), serperApiKey), start, Map.of(
                    "provider", "serper".equalsIgnoreCase(provider) ? "serper" : "ddg"
            ));
        }
    }

    @PostMapping("/test-bangumi")
    public SettingsTestResponse testBangumi(@RequestBody SettingsTestRequests.BangumiTestRequest request) {
        long start = System.nanoTime();
        String proxy = valueOrCurrent(request != null ? request.bangumiProxy() : null, SettingsService.BANGUMI_PROXY);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "seen-app/1.0");
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            ResponseEntity<String> resp = restTemplate.exchange(
                    SettingsService.bangumiApiBase(proxy) + "/subjects/1",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );
            boolean ok = resp.getStatusCode().is2xxSuccessful();
            return response(ok, ok ? "Bangumi 连接正常" : "Bangumi 连接失败", start,
                    Map.of("status", resp.getStatusCode().value()));
        } catch (Exception e) {
            return response(false, sanitize(e.getMessage(), ""), start, Map.of());
        }
    }

    private List<String> testSerperSearch(String query, String apiKey) throws Exception {
        if (apiKey.isBlank()) {
            throw new IllegalArgumentException("Serper API Key 不能为空");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-KEY", apiKey);
        String body = objectMapper.writeValueAsString(Map.of("q", query, "gl", "cn", "hl", "zh-cn"));
        String json = restTemplate.postForObject("https://google.serper.dev/search", new HttpEntity<>(body, headers), String.class);
        return extractSerperTitles(json);
    }

    private List<String> testDdgSearch(String query, String proxy) {
        String url = SettingsService.ddgSearchUrl(proxy) + URLEncoder.encode(query, StandardCharsets.UTF_8);
        String html = restTemplate.getForObject(url, String.class);
        if (html == null || html.isBlank()) return List.of();
        List<String> titles = new ArrayList<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("<a[^>]*href=\"[^\"]*\"[^>]*>([^<]*)")
                .matcher(html);
        while (matcher.find() && titles.size() < 5) {
            String title = unescape(matcher.group(1).trim());
            if (!title.isBlank()) titles.add(title);
        }
        return titles;
    }

    private List<String> extractSerperTitles(String json) throws Exception {
        if (json == null || json.isBlank()) return List.of();
        JsonNode organic = objectMapper.readTree(json).get("organic");
        if (organic == null || !organic.isArray()) return List.of();
        List<String> titles = new ArrayList<>();
        for (JsonNode item : organic) {
            if (item.has("title")) titles.add(item.get("title").asText());
            if (titles.size() >= 5) break;
        }
        return titles;
    }

    private SettingsTestResponse response(boolean ok, String message, long start, Map<String, Object> details) {
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        return new SettingsTestResponse(ok, message != null ? message : "", elapsedMs, details != null ? details : Map.of());
    }

    private static String sanitize(String message, String secret) {
        String result = message == null ? "请求失败" : message;
        if (secret != null && !secret.isBlank()) {
            result = result.replace(secret, "********");
        }
        return result.replaceAll("(?i)(Bearer\\s+)[A-Za-z0-9._\\-]+", "$1********")
                .replaceAll("(?i)(X-API-KEY[:= ]+)[^,\\s]+", "$1********");
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String valueOrCurrent(String value, String key) {
        String normalized = blankToEmpty(value);
        return normalized.isBlank() ? settingsService.getString(key) : normalized;
    }

    private static String trimTrailingSlash(String value) {
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static String unescape(String s) {
        return s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#x27;", "'").replace("&#39;", "'");
    }
}
