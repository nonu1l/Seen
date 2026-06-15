package com.nonu1l.media.service;

import com.nonu1l.media.model.dto.SettingsResponse;
import com.nonu1l.media.model.entity.AppSetting;
import com.nonu1l.media.repository.AppSettingRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 应用运行时设置服务，统一处理数据库持久化、保存、类型转换和敏感值脱敏。
 */
@Service
public class SettingsService {

    public static final String AI_ENABLED = "seen.ai.enabled";
    public static final String AI_TOKEN_USAGE_ENABLED = "seen.ai.token-usage-enabled";
    public static final String OPENAI_BASE_URL = "spring.ai.openai.base-url";
    public static final String OPENAI_API_KEY = "spring.ai.openai.api-key";
    public static final String OPENAI_MODEL = "spring.ai.openai.chat.options.model";
    public static final String OPENAI_TEMPERATURE = "spring.ai.openai.chat.options.temperature";
    public static final String SEARCH_PROVIDER = "seen.search.provider";
    public static final String SERPER_API_KEY = "seen.search.serper-api-key";
    public static final String BANGUMI_PROXY = "seen.bangumi-proxy";
    public static final String DETAIL_CAST_ENABLED = "seen.detail.cast-enabled";

    private static final String MASKED_VALUE = "********";

    private final AppSettingRepository repository;
    private final AtomicReference<Map<String, StoredSetting>> snapshot = new AtomicReference<>(Map.of());

    private final Map<String, SettingDefinition> definitions = buildDefinitions();

    public SettingsService(AppSettingRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    void init() {
        seedMissingSettings();
        refreshSnapshot();
    }

    public SettingsResponse getSettingsResponse() {
        return new SettingsResponse(List.of(
                group("ai", "AI 助手", List.of(
                        item(AI_ENABLED),
                        item(OPENAI_BASE_URL),
                        item(OPENAI_API_KEY),
                        item(OPENAI_MODEL),
                        item(OPENAI_TEMPERATURE),
                        item(AI_TOKEN_USAGE_ENABLED)
                )),
                group("sources", "搜索与数据源", List.of(
                        item(SEARCH_PROVIDER),
                        item(SERPER_API_KEY),
                        item(BANGUMI_PROXY),
                        item(DETAIL_CAST_ENABLED)
                ))
        ), true);
    }

    @Transactional
    public SettingsResponse updateSettings(Map<String, Object> updates) {
        if (updates == null || updates.isEmpty()) {
            return getSettingsResponse();
        }

        for (Map.Entry<String, Object> entry : updates.entrySet()) {
            SettingDefinition definition = definitions.get(entry.getKey());
            if (definition == null) continue;

            String normalized = normalizeValue(definition, entry.getValue());
            if (definition.sensitive() && (normalized.isBlank() || MASKED_VALUE.equals(normalized))) {
                continue;
            }

            AppSetting setting = repository.findBySettingKey(definition.key())
                    .orElseGet(AppSetting::new);
            setting.setSettingKey(definition.key());
            setting.setSettingValue(normalized);
            setting.setValueType(definition.type());
            setting.setSensitive(definition.sensitive());
            repository.save(setting);
        }
        refreshSnapshot();
        return getSettingsResponse();
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

    public AiRuntimeSettings getAiRuntimeSettings() {
        return new AiRuntimeSettings(
                getString(OPENAI_BASE_URL),
                getString(OPENAI_API_KEY),
                getString(OPENAI_MODEL),
                getDouble(OPENAI_TEMPERATURE)
        );
    }

    public String bangumiApiBase() {
        return bangumiApiBase(getString(BANGUMI_PROXY));
    }

    public static String bangumiApiBase(String proxy) {
        String value = proxy == null ? "" : proxy.trim();
        if (value.isBlank()) {
            return "https://api.bgm.tv/v0";
        }
        value = trimTrailingSlash(value);
        return value.endsWith("/api") || value.endsWith("/v0") ? value : value + "/api";
    }

    public static String ddgSearchUrl(String proxy) {
        String value = proxy == null ? "" : proxy.trim();
        if (value.isBlank()) {
            return "https://lite.duckduckgo.com/lite/?q=";
        }
        return trimTrailingSlash(value) + "/search?q=";
    }

    private SettingsResponse.SettingsGroup group(String key, String label, List<SettingsResponse.SettingItem> items) {
        return new SettingsResponse.SettingsGroup(key, label, items);
    }

    private SettingsResponse.SettingItem item(String key) {
        SettingDefinition definition = definitions.get(key);
        Object effective = effectiveValue(key);
        Object value = definition.sensitive() && !String.valueOf(effective).isBlank()
                ? MASKED_VALUE
                : effective;
        return new SettingsResponse.SettingItem(
                key,
                definition.label(),
                value,
                definition.type(),
                definition.sensitive()
        );
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
            AppSetting setting = new AppSetting();
            setting.setSettingKey(definition.key());
            setting.setSettingValue(normalizeValue(definition, definition.defaultValue()));
            setting.setValueType(definition.type());
            setting.setSensitive(definition.sensitive());
            repository.save(setting);
        }
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
            case "number" -> String.valueOf(clampTemperature(value));
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
                default -> value;
            };
        } catch (Exception ignored) {
            return definition.defaultValue();
        }
    }

    private double clampTemperature(String raw) {
        try {
            double value = Double.parseDouble(raw);
            if (value < 0) return 0.0d;
            if (value > 2) return 2.0d;
            return value;
        } catch (Exception ignored) {
            Object fallback = definitions.get(OPENAI_TEMPERATURE).defaultValue();
            return fallback instanceof Number number ? number.doubleValue() : 0.0d;
        }
    }

    private String normalizeProvider(String value) {
        return "serper".equalsIgnoreCase(value) ? "serper" : "ddg";
    }

    private static String trimTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private Map<String, SettingDefinition> buildDefinitions() {
        LinkedHashMap<String, SettingDefinition> map = new LinkedHashMap<>();
        map.put(AI_ENABLED, new SettingDefinition(AI_ENABLED, "AI 助手", "boolean", false, true));
        map.put(AI_TOKEN_USAGE_ENABLED, new SettingDefinition(AI_TOKEN_USAGE_ENABLED, "Token 记录", "boolean", false, true));
        map.put(OPENAI_BASE_URL, new SettingDefinition(OPENAI_BASE_URL, "API Base URL", "string", false, "https://api.deepseek.com"));
        map.put(OPENAI_API_KEY, new SettingDefinition(OPENAI_API_KEY, "API Key", "string", true, ""));
        map.put(OPENAI_MODEL, new SettingDefinition(OPENAI_MODEL, "模型名称", "string", false, "deepseek-v4-flash"));
        map.put(OPENAI_TEMPERATURE, new SettingDefinition(OPENAI_TEMPERATURE, "Temperature", "number", false, 0.0d));
        map.put(SEARCH_PROVIDER, new SettingDefinition(SEARCH_PROVIDER, "搜索源", "select", false, "ddg"));
        map.put(SERPER_API_KEY, new SettingDefinition(SERPER_API_KEY, "Serper API Key", "string", true, ""));
        map.put(BANGUMI_PROXY, new SettingDefinition(BANGUMI_PROXY, "Bangumi 代理地址", "string", false, ""));
        map.put(DETAIL_CAST_ENABLED, new SettingDefinition(DETAIL_CAST_ENABLED, "展示角色 / 演员信息", "boolean", false, true));
        return Collections.unmodifiableMap(map);
    }

    private record SettingDefinition(String key, String label, String type, boolean sensitive, Object defaultValue) {
    }

    private record StoredSetting(String value) {
    }

    public record AiRuntimeSettings(String baseUrl, String apiKey, String model, double temperature) {
    }
}
