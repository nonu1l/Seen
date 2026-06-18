package com.nonu1l.media.service;

import com.nonu1l.media.config.ExternalEndpointProperties;
import com.nonu1l.media.model.dto.AiProviderSettingRequest;
import com.nonu1l.media.model.dto.AiProviderSettingDTO;
import com.nonu1l.media.model.dto.SettingsDTO;
import com.nonu1l.media.model.entity.AppSetting;
import com.nonu1l.media.repository.AppSettingRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 应用运行时设置服务，统一处理数据库持久化、保存和类型转换。
 */
@Service
public class SettingsService {

    public static final String AI_ENABLED = "ai.enabled";
    public static final String AI_TOKEN_USAGE_ENABLED = "ai.token-usage.enabled";
    public static final String AI_MEMORY_ENABLED = "ai.memory.enabled";
    public static final String AI_MEMORY_AUTO_UPDATE_ENABLED = "ai.memory.auto-update.enabled";
    public static final String AI_BASE_URL = "ai.base-url";
    public static final String AI_API_KEY = "ai.api-key";
    public static final String AI_MODEL = "ai.model";
    public static final String AI_TEMPERATURE = "ai.temperature";
    public static final String SEARCH_PROVIDER = "search.provider";
    public static final String SERPER_API_KEY = "search.serper-api-key";
    public static final String TAVILY_API_KEY = "search.tavily-api-key";
    public static final String BANGUMI_PROXY = "source.bangumi-proxy";
    public static final String DETAIL_CAST_ENABLED = "detail.cast-enabled";

    private final AppSettingRepository repository;
    private final ExternalEndpointProperties endpointProperties;
    private final AtomicReference<Map<String, StoredSetting>> snapshot = new AtomicReference<>(Map.of());

    private final Map<String, SettingDefinition> definitions = buildDefinitions();

    public SettingsService(AppSettingRepository repository,
                           ExternalEndpointProperties endpointProperties) {
        this.repository = repository;
        this.endpointProperties = endpointProperties;
    }

    @PostConstruct
    void init() {
        seedMissingSettings();
        refreshSnapshot();
    }

    public SettingsDTO getSettingsResponse() {
        return new SettingsDTO(
                getBoolean(AI_ENABLED),
                getBoolean(AI_TOKEN_USAGE_ENABLED),
                new SettingsDTO.AiMemorySettings(
                        getBoolean(AI_MEMORY_ENABLED),
                        getBoolean(AI_MEMORY_AUTO_UPDATE_ENABLED)
                ),
                getAiProviderSettingResponse(),
                new SettingsDTO.SourceSettings(
                        getString(SEARCH_PROVIDER),
                        hasText(SERPER_API_KEY),
                        getString(SERPER_API_KEY),
                        hasText(TAVILY_API_KEY),
                        getString(TAVILY_API_KEY),
                        getString(BANGUMI_PROXY),
                        getBoolean(DETAIL_CAST_ENABLED)
                )
        );
    }

    @Transactional
    public SettingsDTO updateSettings(Map<String, Object> updates) {
        if (updates == null || updates.isEmpty()) {
            return getSettingsResponse();
        }

        for (Map.Entry<String, Object> entry : updates.entrySet()) {
            SettingDefinition definition = definitions.get(entry.getKey());
            if (definition == null) continue;

            String normalized = normalizeValue(definition, entry.getValue());
            if (definition.sensitive() && normalized.isBlank()) {
                continue;
            }

            saveSetting(definition, normalized);
        }
        refreshSnapshot();
        return getSettingsResponse();
    }

    public AiProviderSettingDTO getAiProviderSettingResponse() {
        return new AiProviderSettingDTO(
                getString(AI_BASE_URL),
                getString(AI_MODEL),
                getDouble(AI_TEMPERATURE),
                hasText(AI_API_KEY),
                getString(AI_API_KEY)
        );
    }

    @Transactional
    public AiProviderSettingDTO updateAiProviderSetting(AiProviderSettingRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }

        saveSetting(definitions.get(AI_BASE_URL), AiProviderSupport.trimTrailingSlash(request.baseUrl() != null ? request.baseUrl().trim() : ""));
        saveSetting(definitions.get(AI_MODEL), request.model() != null ? request.model().trim() : "");
        saveSetting(definitions.get(AI_TEMPERATURE), String.valueOf(clampTemperature(request.temperature())));

        if (request.apiKey() != null) {
            saveSetting(definitions.get(AI_API_KEY), request.apiKey().trim());
        }

        refreshSnapshot();
        return getAiProviderSettingResponse();
    }

    public AiRuntimeSetting currentRuntimeSetting() {
        String baseUrl = AiProviderSupport.trimTrailingSlash(getString(AI_BASE_URL));
        return new AiRuntimeSetting(
                null,
                AiProviderSupport.inferProviderKind(baseUrl),
                baseUrl,
                getString(AI_API_KEY),
                getString(AI_MODEL),
                getDouble(AI_TEMPERATURE)
        );
    }

    public AiRuntimeSetting runtimeFromDraft(AiProviderSettingRequest request) {
        String currentBaseUrl = AiProviderSupport.trimTrailingSlash(getString(AI_BASE_URL));
        String baseUrl = request != null && request.baseUrl() != null
                ? AiProviderSupport.trimTrailingSlash(request.baseUrl().trim())
                : currentBaseUrl;
        String model = request != null && request.model() != null
                ? request.model().trim()
                : getString(AI_MODEL);
        String apiKey = resolveAiApiKey(request);
        double temperature = request != null && request.temperature() != null
                ? clampTemperature(request.temperature())
                : getDouble(AI_TEMPERATURE);
        return new AiRuntimeSetting(null, AiProviderSupport.inferProviderKind(baseUrl), baseUrl, apiKey, model, temperature);
    }

    public boolean getBoolean(String key) {
        return Boolean.parseBoolean(String.valueOf(effectiveValue(key)));
    }

    public String getString(String key) {
        Object value = effectiveValue(key);
        return value == null ? "" : String.valueOf(value);
    }

    public double getDouble(String key) {
        Object value = effectiveValue(key);
        if (value instanceof Number number) return number.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            SettingDefinition definition = definitions.get(key);
            Object fallback = definition != null ? definition.defaultValue() : 0.0d;
            return fallback instanceof Number number ? number.doubleValue() : 0.0d;
        }
    }

    public boolean hasText(String key) {
        String value = getString(key);
        return value != null && !value.isBlank();
    }

    public String bangumiApiBase() {
        return bangumiApiBase(getString(BANGUMI_PROXY), endpointProperties.getBangumiApiBase());
    }

    public String bangumiApiBase(String proxy) {
        return bangumiApiBase(proxy, endpointProperties.getBangumiApiBase());
    }

    private static String bangumiApiBase(String proxy, String defaultApiBase) {
        String value = proxy == null ? "" : proxy.trim();
        if (value.isBlank()) {
            return AiProviderSupport.trimTrailingSlash(defaultApiBase);
        }
        value = AiProviderSupport.trimTrailingSlash(value);
        return value.endsWith("/api") || value.endsWith("/v0") ? value : value + "/api";
    }

    private Object effectiveValue(String key) {
        SettingDefinition definition = definitions.get(key);
        if (definition == null) {
            throw new IllegalArgumentException("Unsupported setting key: " + key);
        }

        StoredSetting stored = snapshot.get().get(key);
        if (stored != null) {
            return convert(definition, stored.value());
        }

        return definition.defaultValue();
    }

    private void seedMissingSettings() {
        for (SettingDefinition definition : definitions.values()) {
            if (repository.findBySettingKey(definition.key()).isPresent()) {
                continue;
            }
            saveSetting(definition, normalizeValue(definition, definition.defaultValue()));
        }
    }

    private void saveSetting(SettingDefinition definition, String normalized) {
        AppSetting setting = repository.findBySettingKey(definition.key())
                .orElseGet(AppSetting::new);
        setting.setSettingKey(definition.key());
        setting.setSettingValue(normalized);
        setting.setValueType(definition.type());
        setting.setSensitive(definition.sensitive());
        repository.save(setting);
    }

    private void refreshSnapshot() {
        Map<String, StoredSetting> values = new HashMap<>();
        for (AppSetting setting : repository.findAll()) {
            if (definitions.containsKey(setting.getSettingKey())) {
                values.put(setting.getSettingKey(), new StoredSetting(setting.getSettingValue()));
            }
        }
        snapshot.set(Collections.unmodifiableMap(values));
    }

    private String normalizeValue(SettingDefinition definition, Object raw) {
        if (raw == null) return "";
        String value = String.valueOf(raw).trim();
        return switch (definition.type()) {
            case "boolean" -> String.valueOf(Boolean.parseBoolean(value));
            case "select" -> normalizeProvider(value);
            default -> value;
        };
    }

    private Object convert(SettingDefinition definition, String value) {
        if (value == null) return definition.defaultValue();
        try {
            return switch (definition.type()) {
                case "boolean" -> Boolean.parseBoolean(value);
                case "number" -> Double.parseDouble(value);
                case "select" -> normalizeProvider(value);
                default -> value;
            };
        } catch (Exception ignored) {
            return definition.defaultValue();
        }
    }

    private String normalizeProvider(String value) {
        if ("tavily".equalsIgnoreCase(value)) return "tavily";
        return "serper";
    }

    private String resolveAiApiKey(AiProviderSettingRequest request) {
        if (request == null) {
            return getString(AI_API_KEY);
        }
        if (request.apiKey() != null) {
            return request.apiKey().trim();
        }
        return getString(AI_API_KEY);
    }

    private static double clampTemperature(Double raw) {
        if (raw == null || raw.isNaN()) return 0.0d;
        if (raw < 0.0d) return 0.0d;
        if (raw > 2.0d) return 2.0d;
        return raw;
    }

    private Map<String, SettingDefinition> buildDefinitions() {
        LinkedHashMap<String, SettingDefinition> map = new LinkedHashMap<>();
        map.put(AI_ENABLED, new SettingDefinition(AI_ENABLED, "AI 助手", "boolean", false, true));
        map.put(AI_TOKEN_USAGE_ENABLED, new SettingDefinition(AI_TOKEN_USAGE_ENABLED, "Token 记录", "boolean", false, true));
        map.put(AI_MEMORY_ENABLED, new SettingDefinition(AI_MEMORY_ENABLED, "AI 长期记忆", "boolean", false, true));
        map.put(AI_MEMORY_AUTO_UPDATE_ENABLED, new SettingDefinition(AI_MEMORY_AUTO_UPDATE_ENABLED, "AI 长期记忆自动更新", "boolean", false, true));
        map.put(AI_BASE_URL, new SettingDefinition(AI_BASE_URL, "AI Base URL", "string", false, ""));
        map.put(AI_API_KEY, new SettingDefinition(AI_API_KEY, "AI API Key", "string", true, ""));
        map.put(AI_MODEL, new SettingDefinition(AI_MODEL, "AI 模型", "string", false, ""));
        map.put(AI_TEMPERATURE, new SettingDefinition(AI_TEMPERATURE, "AI Temperature", "number", false, 0.0d));
        map.put(SEARCH_PROVIDER, new SettingDefinition(SEARCH_PROVIDER, "搜索源", "select", false, "serper"));
        map.put(SERPER_API_KEY, new SettingDefinition(SERPER_API_KEY, "Serper API Key", "string", true, ""));
        map.put(TAVILY_API_KEY, new SettingDefinition(TAVILY_API_KEY, "Tavily API Key", "string", true, ""));
        map.put(BANGUMI_PROXY, new SettingDefinition(BANGUMI_PROXY, "Bangumi 代理地址", "string", false, ""));
        map.put(DETAIL_CAST_ENABLED, new SettingDefinition(DETAIL_CAST_ENABLED, "展示角色 / 演员信息", "boolean", false, true));
        return Collections.unmodifiableMap(map);
    }

    private record SettingDefinition(String key, String label, String type, boolean sensitive, Object defaultValue) {
    }

    private record StoredSetting(String value) {
    }

    public record AiRuntimeSetting(
            Long id,
            String providerKind,
            String baseUrl,
            String apiKey,
            String model,
            double temperature
    ) {
    }
}
