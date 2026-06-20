package com.nonu1l.media.service;

import com.nonu1l.media.config.ExternalEndpointProperties;
import com.nonu1l.media.model.dto.AiProviderSettingRequest;
import com.nonu1l.media.model.dto.test.SettingsTestRequests;
import com.nonu1l.media.model.dto.test.SettingsTestResponse;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
 * 设置页连接测试服务，支持测试当前表单草稿和已保存设置。
 */
@Service
public class SettingsTestService {

    private final SettingsService settingsService;
    private final ExternalEndpointProperties endpointProperties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AiChatClientFactory chatClientFactory;
    private final String defaultQuery;
    private final int previewTitleLimit;

    public SettingsTestService(SettingsService settingsService,
                               ExternalEndpointProperties endpointProperties,
                               RestTemplateBuilder builder,
                               ObjectMapper objectMapper,
                               AiChatClientFactory chatClientFactory,
                               @Value("${app.runtime.settings-test.query:孤独摇滚}") String defaultQuery,
                               @Value("${app.runtime.settings-test.connect-timeout:10s}") Duration connectTimeout,
                               @Value("${app.runtime.settings-test.read-timeout:20s}") Duration readTimeout,
                               @Value("${app.runtime.settings-test.preview-title-limit:5}") int previewTitleLimit) {
        this.settingsService = settingsService;
        this.endpointProperties = endpointProperties;
        this.restTemplate = builder
                .connectTimeout(connectTimeout)
                .readTimeout(readTimeout)
                .build();
        this.objectMapper = objectMapper;
        this.chatClientFactory = chatClientFactory;
        this.defaultQuery = defaultQuery;
        this.previewTitleLimit = Math.max(1, previewTitleLimit);
    }

    public SettingsTestResponse testAiProfile(SettingsTestRequests.AiTestRequest request) {
        long start = System.nanoTime();
        SettingsService.AiRuntimeSetting setting = resolveTestSetting(request);
        if (setting.baseUrl().isBlank() || setting.apiKey().isBlank() || setting.model().isBlank()) {
            return response(false, "baseUrl、apiKey、model 不能为空", start,
                    Map.of("provider", setting.profileName(), "model", setting.model()));
        }

        try {
            String content = chatClientFactory.clientFor(setting, AiThinkingMode.DISABLED)
                    .prompt()
                    .user("ping")
                    .call()
                    .content();
            boolean ok = content != null;
            return response(ok, ok ? "连接正常" : "连接失败", start, Map.of(
                    "provider", setting.profileName(),
                    "model", setting.model()
            ));
        } catch (Exception e) {
            return response(false, sanitize(e.getMessage(), setting.apiKey()), start, Map.of(
                    "provider", setting.profileName(),
                    "model", setting.model()
            ));
        }
    }

    public SettingsTestResponse testSearch(SettingsTestRequests.SearchTestRequest request) {
        long start = System.nanoTime();
        String provider = normalizeProvider(request != null ? request.provider() : null);
        String serperApiKey = valueOrCurrent(request != null ? request.serperApiKey() : null, SettingsService.SERPER_API_KEY);
        String tavilyApiKey = valueOrCurrent(request != null ? request.tavilyApiKey() : null, SettingsService.TAVILY_API_KEY);
        String query = request != null && request.query() != null && !request.query().isBlank()
                ? request.query().trim()
                : defaultQuery;
        return testSingleSearch(start, provider, query, serperApiKey, tavilyApiKey);
    }

    public SettingsTestResponse testBangumi(SettingsTestRequests.BangumiTestRequest request) {
        long start = System.nanoTime();
        String proxy = valueOrCurrent(request != null ? request.bangumiProxy() : null, SettingsService.BANGUMI_PROXY);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "seen-app/1.0");
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            ResponseEntity<String> resp = restTemplate.exchange(
                    settingsService.bangumiApiBase(proxy) + "/subjects/1",
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

    private SettingsService.AiRuntimeSetting resolveTestSetting(SettingsTestRequests.AiTestRequest request) {
        return settingsService.runtimeFromDraft(new AiProviderSettingRequest(
                request != null ? request.baseUrl() : null,
                request != null ? request.model() : null,
                request != null ? request.temperature() : null,
                null,
                request != null ? request.apiKey() : null
        ));
    }

    private List<String> testSerperSearch(String query, String apiKey) throws Exception {
        if (apiKey.isBlank()) {
            throw new IllegalArgumentException("Serper API Key 不能为空");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-KEY", apiKey);
        String body = objectMapper.writeValueAsString(Map.of("q", query, "gl", "cn", "hl", "zh-cn"));
        String json = restTemplate.postForObject(endpointProperties.getSerperSearchUrl(), new HttpEntity<>(body, headers), String.class);
        return extractSerperTitles(json);
    }

    private List<String> testTavilySearch(String query, String apiKey) throws Exception {
        if (apiKey.isBlank()) {
            throw new IllegalArgumentException("Tavily API Key 不能为空");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", query);
        body.put("search_depth", "basic");
        body.put("max_results", previewTitleLimit);
        body.put("include_answer", false);
        body.put("include_raw_content", false);
        String json = restTemplate.postForObject(endpointProperties.getTavilySearchUrl(), new HttpEntity<>(objectMapper.writeValueAsString(body), headers), String.class);
        return extractTavilyTitles(json);
    }

    private SettingsTestResponse testSingleSearch(long start, String provider, String query,
                                                  String serperApiKey, String tavilyApiKey) {
        if (SettingsService.SEARCH_PROVIDER_DISABLED.equals(provider)) {
            return response(true, "搜索源已关闭，无需测试", start, Map.of("provider", provider));
        }
        List<Map<String, Object>> attempts = new ArrayList<>();
        List<String> titles = trySearch(provider, query, serperApiKey, tavilyApiKey, attempts);
        if (titles.isEmpty()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("provider", provider);
            details.put("attempts", attempts);
            return response(false, "搜索不可用或无结果", start, details);
        }
        return searchTestResponse(true, "搜索连接正常", start, provider, titles, attempts);
    }

    private List<String> trySearch(String provider, String query, String serperApiKey,
                                   String tavilyApiKey, List<Map<String, Object>> attempts) {
        long start = System.nanoTime();
        Map<String, Object> attempt = new LinkedHashMap<>();
        attempt.put("provider", provider);
        try {
            List<String> titles = "serper".equals(provider)
                    ? testSerperSearch(query, serperApiKey)
                    : testTavilySearch(query, tavilyApiKey);
            attempt.put("ok", !titles.isEmpty());
            attempt.put("count", titles.size());
            attempt.put("elapsedMs", (System.nanoTime() - start) / 1_000_000);
            attempts.add(attempt);
            return titles;
        } catch (Exception e) {
            attempt.put("ok", false);
            attempt.put("error", sanitize(sanitize(e.getMessage(), serperApiKey), tavilyApiKey));
            attempt.put("elapsedMs", (System.nanoTime() - start) / 1_000_000);
            attempts.add(attempt);
            return List.of();
        }
    }

    private SettingsTestResponse searchTestResponse(boolean ok, String message, long start, String provider,
                                                    List<String> titles, List<Map<String, Object>> attempts) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("provider", provider);
        details.put("count", titles.size());
        details.put("titles", titles.stream()
                .limit(previewTitleLimit).toList());
        details.put("attempts", attempts);
        return response(ok, message, start, details);
    }

    private List<String> extractSerperTitles(String json) throws Exception {
        if (json == null || json.isBlank()) return List.of();
        JsonNode organic = objectMapper.readTree(json).get("organic");
        if (organic == null || !organic.isArray()) return List.of();
        List<String> titles = new ArrayList<>();
        for (JsonNode item : organic) {
            if (item.has("title")) titles.add(item.get("title").asText());
            if (titles.size() >= previewTitleLimit) break;
        }
        return titles;
    }

    private List<String> extractTavilyTitles(String json) throws Exception {
        if (json == null || json.isBlank()) return List.of();
        JsonNode results = objectMapper.readTree(json).get("results");
        if (results == null || !results.isArray()) return List.of();
        List<String> titles = new ArrayList<>();
        for (JsonNode item : results) {
            if (item.has("title")) titles.add(item.get("title").asText());
            if (titles.size() >= previewTitleLimit) break;
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

    private String normalizeProvider(String value) {
        String normalized = blankToEmpty(value);
        if (normalized.isBlank()) {
            normalized = settingsService.getString(SettingsService.SEARCH_PROVIDER);
        }
        if ("serper".equalsIgnoreCase(normalized)) return "serper";
        if ("tavily".equalsIgnoreCase(normalized)) return "tavily";
        if (SettingsService.SEARCH_PROVIDER_DISABLED.equalsIgnoreCase(normalized)
                || "none".equalsIgnoreCase(normalized)
                || "off".equalsIgnoreCase(normalized)) {
            return SettingsService.SEARCH_PROVIDER_DISABLED;
        }
        return "serper";
    }

    private String valueOrCurrent(String value, String key) {
        String normalized = blankToEmpty(value);
        return normalized.isBlank() ? settingsService.getString(key) : normalized;
    }
}
