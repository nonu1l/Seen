package com.nonu1l.media.service;

import com.nonu1l.media.model.dto.WebSearchItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    /**
     * 注入底层搜索实现，实际请求时按当前设置动态选择。
     *
     * @param ddg DDG 备选实现
     * @param serper Serper 实现
     * @param settingsService 设置读取服务
     * @param webFetchService 独立网页抓取服务
     */
    public WebSearchService(DDGSearchService ddg, SerperSearchService serper,
                            SettingsService settingsService,
                            WebFetchService webFetchService) {
        this.ddg = ddg;
        this.serper = serper;
        this.settingsService = settingsService;
        this.webFetchService = webFetchService;
    }

    /**
     * 搜索外部网页。
     *
     * @param query 检索关键词
     * @return 委托实现返回的结果
     */
    @Override
    public List<WebSearchItem> search(String query) {
        String provider = settingsService.getString(SettingsService.SEARCH_PROVIDER);
        if ("auto".equalsIgnoreCase(provider)) {
            return searchAuto(query);
        }
        if ("serper".equalsIgnoreCase(provider)) {
            return serper.isAvailable() ? searchWithProvider("serper", serper, query) : List.of();
        }
        return searchWithProvider("ddg", ddg, query);
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

    private List<WebSearchItem> searchAuto(String query) {
        if (serper.isAvailable()) {
            List<WebSearchItem> serperResults = searchWithProvider("serper", serper, query);
            if (!serperResults.isEmpty()) {
                return serperResults;
            }
            log.info("Serper returned no usable results for '{}', falling back to DuckDuckGo", query);
        } else {
            log.info("Serper API key is missing, using DuckDuckGo for '{}'", query);
        }
        return searchWithProvider("ddg", ddg, query);
    }

    private List<WebSearchItem> searchWithProvider(String providerName, SearchProvider provider, String query) {
        long start = System.nanoTime();
        try {
            List<WebSearchItem> results = provider.search(query);
            log.info("Web search provider={} query='{}' results={} elapsedMs={}",
                    providerName, query, results.size(), (System.nanoTime() - start) / 1_000_000);
            return results;
        } catch (Exception e) {
            log.warn("Web search provider={} failed query='{}': {}", providerName, query, e.getMessage());
            return List.of();
        }
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
