package com.nonu1l.media.service;

import com.nonu1l.media.model.dto.WebSearchItemDTO;
import com.nonu1l.media.model.dto.WebSearchToolResultDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Web 搜索路由 — 根据配置选择 DDG 或 Serper。
 */
@Service
public class WebSearchService implements SearchProvider {

    private static final Logger log = LoggerFactory.getLogger(WebSearchService.class);

    private final DDGSearchService ddg;
    private final SerperSearchService serper;
    private final SettingsService settingsService;
    private final WebFetchService webFetchService;
    private final boolean searchEnabled;

    /**
     * 注入底层搜索实现，实际请求时按当前设置动态选择。
     *
     * @param ddg DDG 备选实现
     * @param serper Serper 实现
     * @param settingsService 设置读取服务
     * @param webFetchService 独立网页抓取服务
     * @param searchEnabled 是否启用外部搜索源
     */
    public WebSearchService(DDGSearchService ddg, SerperSearchService serper,
                            SettingsService settingsService,
                            WebFetchService webFetchService,
                            @Value("${app.search.enabled:true}") boolean searchEnabled) {
        this.ddg = ddg;
        this.serper = serper;
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
        if ("auto".equalsIgnoreCase(provider)) {
            return searchAuto(query);
        }
        if ("serper".equalsIgnoreCase(provider)) {
            if (!serper.isAvailable()) {
                return new WebSearchToolResultDTO(false, query, "serper", 0, List.of(),
                        "Serper API key is missing", "可以切换搜索源为 auto/ddg，或让用户配置 Serper API Key。");
            }
            return searchWithProvider("serper", query);
        }
        return searchWithProvider("ddg", query);
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

    private WebSearchToolResultDTO searchAuto(String query) {
        if (serper.isAvailable()) {
            WebSearchToolResultDTO serperResult = searchWithProvider("serper", query);
            if (serperResult.ok()) {
                return serperResult;
            }
            log.info("Serper returned no usable results for '{}', falling back to DuckDuckGo", query);
            WebSearchToolResultDTO ddgResult = searchWithProvider("ddg", query);
            if (ddgResult.ok()) {
                return ddgResult;
            }
            String error = serperResult.error() + "; " + ddgResult.error();
            return new WebSearchToolResultDTO(false, query, "auto", 0, List.of(), error,
                    "两个搜索源都没有返回可用结果，可以改写关键词，或用 fetch_url 直接访问公开资料源。");
        } else {
            log.info("Serper API key is missing, using DuckDuckGo for '{}'", query);
        }
        return searchWithProvider("ddg", query);
    }

    private WebSearchToolResultDTO searchWithProvider(String providerName, String query) {
        long start = System.nanoTime();
        WebSearchToolResultDTO result = "serper".equals(providerName)
                ? serper.searchWithDiagnostics(query)
                : ddg.searchWithDiagnostics(query);
        log.info("Web search provider={} query='{}' results={} ok={} elapsedMs={}",
                providerName, query, result.count(), result.ok(), (System.nanoTime() - start) / 1_000_000);
        return result;
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
