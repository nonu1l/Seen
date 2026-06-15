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

    /**
     * 注入底层搜索实现，实际请求时按当前设置动态选择。
     *
     * @param ddg DDG 备选实现
     * @param serper Serper 实现
     * @param settingsService 设置读取服务
     */
    public WebSearchService(DDGSearchService ddg, SerperSearchService serper,
                            SettingsService settingsService) {
        this.ddg = ddg;
        this.serper = serper;
        this.settingsService = settingsService;
    }

    /**
     * 搜索外部网页。
     *
     * @param query 检索关键词
     * @return 委托实现返回的结果
     */
    @Override
    public List<WebSearchItem> search(String query) {
        return delegate().search(query);
    }

    /**
     * 抓取网页正文文本。
     *
     * @param url 目标 URL
     * @return 清洗后的文本
     */
    @Override
    public String fetch(String url) {
        String raw = delegate().fetch(url);
        return cleanFetchedText(raw);
    }

    private SearchProvider delegate() {
        String provider = settingsService.getString(SettingsService.SEARCH_PROVIDER);
        if ("serper".equalsIgnoreCase(provider)) {
            if (serper.isAvailable()) {
                return serper;
            }
            log.warn("Search provider is serper but API key is missing, falling back to DuckDuckGo");
        }
        return ddg;
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
