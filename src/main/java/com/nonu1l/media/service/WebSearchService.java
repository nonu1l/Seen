package com.nonu1l.media.service;

import com.nonu1l.media.model.dto.WebSearchItemDTO;
import com.nonu1l.media.model.dto.WebSearchToolResultDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Web 搜索路由，根据设置页选择的 provider 委托给对应搜索策略。
 */
@Service
public class WebSearchService implements SearchProvider {

    private static final Logger log = LoggerFactory.getLogger(WebSearchService.class);

    private final Map<String, WebSearchProviderStrategy> providers;
    private final SettingsService settingsService;
    private final WebFetchService webFetchService;
    private final boolean searchEnabled;

    /**
     * 注入底层搜索实现，实际请求时按当前设置动态选择。
     *
     * @param providerStrategies 可用搜索源策略列表
     * @param settingsService 设置读取服务
     * @param webFetchService 独立网页抓取服务
     * @param searchEnabled 是否启用外部搜索源
     */
    public WebSearchService(List<WebSearchProviderStrategy> providerStrategies,
                            SettingsService settingsService,
                            WebFetchService webFetchService,
                            @Value("${app.search.enabled:true}") boolean searchEnabled) {
        Map<String, WebSearchProviderStrategy> providerMap = new LinkedHashMap<>();
        for (WebSearchProviderStrategy strategy : providerStrategies) {
            providerMap.put(strategy.providerKey(), strategy);
        }
        this.providers = Map.copyOf(providerMap);
        this.settingsService = settingsService;
        this.webFetchService = webFetchService;
        this.searchEnabled = searchEnabled;
    }

    /**
     * 搜索外部网页。
     *
     * @param query 检索关键词
     * @return 委托实现返回的结果
     */
    @Override
    public List<WebSearchItemDTO> search(String query) {
        return searchWithDiagnostics(query).items();
    }

    /**
     * 搜索外部网页并返回 AI 可理解的诊断结果。
     *
     * @param query 检索关键词
     * @return 包含搜索结果、搜索源或失败原因的结构化结果
     */
    public WebSearchToolResultDTO searchWithDiagnostics(String query) {
        if (!isSearchEnabled()) {
            log.warn("Web search disabled by app.search.enabled=false");
            return new WebSearchToolResultDTO(false, query, "disabled", 0, List.of(),
                    "Web search disabled by app.search.enabled=false", "请告知用户当前外部搜索已关闭，或改用本地记录/已有知识。");
        }
        String provider = settingsService.getString(SettingsService.SEARCH_PROVIDER);
        WebSearchProviderStrategy strategy = providers.get(provider);
        if (strategy == null) {
            return new WebSearchToolResultDTO(false, query, provider, 0, List.of(),
                    "Unsupported web search provider: " + provider, "请在设置页选择 Serper 或 Tavily。");
        }
        if (!strategy.isAvailable()) {
            return new WebSearchToolResultDTO(false, query, strategy.providerKey(), 0, List.of(),
                    displayName(strategy.providerKey()) + " API key is missing", null);
        }
        return searchWithProvider(strategy, query);
    }

    /**
     * 抓取网页正文文本。
     *
     * @param url 目标 URL
     * @return 清洗后的文本
     */
    @Override
    public String fetch(String url) {
        return cleanFetchedText(webFetchService.fetchText(url));
    }

    private WebSearchToolResultDTO searchWithProvider(WebSearchProviderStrategy strategy, String query) {
        long start = System.nanoTime();
        WebSearchToolResultDTO result = strategy.searchWithDiagnostics(query);
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

    // ── 抓取文本清洗 ──

    private static final Pattern HEX_GARBAGE = Pattern.compile("\\b[0-9a-fA-F]{20,}\\b");
    private static final Pattern TEMPLATE_VAR = Pattern.compile("\\{\\{[^}]*}}");
    private static final Pattern PERCENT_SEQ = Pattern.compile("(%[0-9A-Fa-f]{2})+");

    static String cleanFetchedText(String raw) {
        if (raw == null || raw.isBlank()) return raw;

        String text = raw;

        // 1. URL 解码百分号编码的中文
        text = PERCENT_SEQ.matcher(text).replaceAll(m -> {
            try {
                return URLDecoder.decode(m.group(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                return m.group();
            }
        });

        // 2. 去掉模板变量 {{...}}
        text = TEMPLATE_VAR.matcher(text).replaceAll(" ");

        // 3. 去掉长 hex 垃圾串（B站/爱奇艺页面里的随机 ID）
        text = HEX_GARBAGE.matcher(text).replaceAll(" ");

        // 4. 去掉常见加载占位文本
        text = text.replace("载入中 ...", " ").replace("loading...", " ");

        // 5. 压缩空白
        text = text.replaceAll("\\s+", " ").trim();

        return text;
    }
}
