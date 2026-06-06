package com.nonu1l.media.service;

import com.nonu1l.media.model.dto.WebSearchItem;
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

    private final SearchProvider delegate;

    public WebSearchService(DDGSearchService ddg, SerperSearchService serper,
                            @Value("${seen.search.provider:ddg}") String provider) {
        if ("serper".equalsIgnoreCase(provider) && serper.isAvailable()) {
            this.delegate = serper;
            log.info("Search provider: Serper.dev");
        } else {
            this.delegate = ddg;
            log.info("Search provider: DuckDuckGo (via proxy)");
        }
    }

    @Override
    public List<WebSearchItem> search(String query) {
        return delegate.search(query);
    }

    @Override
    public String fetch(String url) {
        String raw = delegate.fetch(url);
        return cleanFetchedText(raw);
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
