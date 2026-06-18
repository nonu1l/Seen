package com.nonu1l.media.service;

import com.nonu1l.media.model.dto.WebSearchResultDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Web 搜索路由，根据设置页选择的 provider 委托给对应搜索策略。
 */
@Service
public class WebSearchService {

    private static final Logger log = LoggerFactory.getLogger(WebSearchService.class);

    private final Map<String, WebSearchProvider> providers;
    private final SettingsService settingsService;
    private final boolean searchEnabled;

    /**
     * 注入底层搜索实现，实际请求时按当前设置动态选择。
     *
     * @param providerStrategies 可用搜索源策略列表
     * @param settingsService 设置读取服务
     * @param searchEnabled 是否启用外部搜索源
     */
    public WebSearchService(List<WebSearchProvider> providerStrategies,
                            SettingsService settingsService,
                            @Value("${app.search.enabled:true}") boolean searchEnabled) {
        Map<String, WebSearchProvider> providerMap = new LinkedHashMap<>();
        for (WebSearchProvider strategy : providerStrategies) {
            providerMap.put(strategy.providerKey(), strategy);
        }
        this.providers = Map.copyOf(providerMap);
        this.settingsService = settingsService;
        this.searchEnabled = searchEnabled;
    }

    /**
     * 搜索外部网页并返回 AI 可理解的诊断结果。
     *
     * @param query 检索关键词
     * @return 包含搜索结果、搜索源或失败原因的结构化结果
     */
    public WebSearchResultDTO searchWithDiagnostics(String query) {
        if (!isSearchEnabled()) {
            log.warn("Web search disabled by app.search.enabled=false");
            return new WebSearchResultDTO(false, query, "disabled", 0, List.of(),
                    "Web search disabled by app.search.enabled=false", "请告知用户当前外部搜索已关闭，或改用本地记录/已有知识。");
        }
        String provider = settingsService.getString(SettingsService.SEARCH_PROVIDER);
        WebSearchProvider strategy = providers.get(provider);
        if (strategy == null) {
            return new WebSearchResultDTO(false, query, provider, 0, List.of(),
                    "Unsupported web search provider: " + provider, "请在设置页选择 Serper 或 Tavily。");
        }
        if (!strategy.isAvailable()) {
            return new WebSearchResultDTO(false, query, strategy.providerKey(), 0, List.of(),
                    displayName(strategy.providerKey()) + " API key is missing", null);
        }
        return searchWithProvider(strategy, query);
    }

    private WebSearchResultDTO searchWithProvider(WebSearchProvider strategy, String query) {
        long start = System.nanoTime();
        WebSearchResultDTO result = strategy.searchWithDiagnostics(query);
        log.info("Web search provider={} query='{}' results={} ok={} elapsedMs={}",
                strategy.providerKey(), query, result.count(), result.ok(), (System.nanoTime() - start) / 1_000_000);
        return result;
    }

    private static String displayName(String provider) {
        if ("tavily".equalsIgnoreCase(provider)) return "Tavily";
        return "Serper";
    }

    /**
     * 判断外部搜索源是否启用，用于在联调或测试中模拟搜索源整体不可用。
     *
     * @return 配置允许外部搜索时返回 true
     */
    private boolean isSearchEnabled() {
        return searchEnabled;
    }
}
