package com.nonu1l.media.service;

import com.nonu1l.media.config.ExternalEndpointProperties;
import com.nonu1l.media.model.dto.AiProviderSettingRequest;
import com.nonu1l.media.model.dto.SettingsTestRequests;
import com.nonu1l.media.model.dto.SettingsTestResponse;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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

    private static final String TEST_QUERY = "孤独摇滚";

    private final SettingsService settingsService;
    private final ExternalEndpointProperties endpointProperties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public SettingsTestService(SettingsService settingsService,
                               ExternalEndpointProperties endpointProperties,
                               RestTemplateBuilder builder,
                               ObjectMapper objectMapper) {
        this.settingsService = settingsService;
        this.endpointProperties = endpointProperties;
        this.restTemplate = builder
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(20))
                .build();
        this.objectMapper = objectMapper;
    }

    public SettingsTestResponse testAiProfile(SettingsTestRequests.AiTestRequest request) {
        long start = System.nanoTime();
        SettingsService.AiRuntimeSetting setting = resolveTestSetting(request);
        if (setting.baseUrl().isBlank() || setting.apiKey().isBlank() || setting.model().isBlank()) {
            return response(false, "baseUrl、apiKey、model 不能为空", start,
                    Map.of("provider", setting.providerKind(), "model", setting.model()));
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(setting.apiKey());

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", setting.model());
            body.put("temperature", Math.max(0.0d, Math.min(2.0d, setting.temperature())));
            body.put("max_tokens", 8);
            body.put("messages", List.of(Map.of("role", "user", "content", "ping")));
            if (AiProviderSupport.usesThinkingToggle(setting.providerKind())) {
                body.put("thinking", Map.of("type", "disabled"));
            }

            ResponseEntity<String> resp = restTemplate.exchange(
                    AiProviderSupport.chatCompletionsUrl(setting.baseUrl()),
                    HttpMethod.POST,
                    new HttpEntity<>(objectMapper.writeValueAsString(body), headers),
                    String.class
            );
            boolean ok = resp.getStatusCode().is2xxSuccessful();
            return response(ok, ok ? "连接正常" : "连接失败", start, Map.of(
                    "provider", setting.providerKind(),
                    "model", setting.model()
            ));
        } catch (Exception e) {
            return response(false, sanitize(e.getMessage(), setting.apiKey()), start, Map.of(
                    "provider", setting.providerKind(),
                    "model", setting.model()
            ));
        }
    }

    public SettingsTestResponse testSearch(SettingsTestRequests.SearchTestRequest request) {
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
                request != null ? request.apiKey() : null,
                request != null ? request.clearApiKey() : false
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

    private List<String> testDdgSearch(String query, String proxy) {
        String url = settingsService.ddgSearchUrl(proxy) + URLEncoder.encode(query, StandardCharsets.UTF_8);
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

    private static String unescape(String s) {
        return s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#x27;", "'").replace("&#39;", "'");
    }
}
