package com.nonu1l.media.service;

import com.nonu1l.media.model.dto.WebSearchItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DuckDuckGo 搜索 — 通过 CF Worker 代理 DDG Lite。
 */
@Service
public class DDGSearchService implements SearchProvider {

    private static final Logger log = LoggerFactory.getLogger(DDGSearchService.class);
    private static final int MAX_RESULTS = 10;

    private static final Pattern LINK_PAT = Pattern.compile("<a[^>]*href=\"([^\"]*)\"[^>]*>([^<]*)");
    private static final Pattern SNIPPET_PAT = Pattern.compile(
        "class=['\"]result-snippet['\"][^>]*>(.+?)</td>", Pattern.DOTALL);

    private final RestTemplate restTemplate;
    private final SettingsService settingsService;

    /**
     * @param builder RestTemplate 构造器
     * @param settingsService 设置读取服务
     */
    public DDGSearchService(RestTemplateBuilder builder,
                            SettingsService settingsService) {
        this.restTemplate = builder
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(10))
                .build();
        this.settingsService = settingsService;
    }

    /**
     * 使用 DDG Lite 页面解析搜索结果。
     *
     * <p>内建重试与限额保护，最多尝试 3 次，并返回最多 10 条结果。</p>
     *
     * @param query 检索关键词
     * @return 标准化搜索结果
     */
    @Override
    public List<WebSearchItem> search(String query) {
        List<WebSearchItem> results = new ArrayList<>();
        try {
            int searchCount = 0;
            while (results.isEmpty() && searchCount < 3) {
                String searchUrl = SettingsService.ddgSearchUrl(settingsService.getString(SettingsService.BANGUMI_PROXY));
                String html = restTemplate.getForObject(
                        searchUrl + URLEncoder.encode(query, StandardCharsets.UTF_8), String.class);
                if (html == null) break;

                String[] rows = html.split("<tr[ >]");
                String lastLink = null, lastTitle = null;
                for (String row : rows) {
                    Matcher lm = LINK_PAT.matcher(row);
                    if (lm.find() && !lm.group(2).trim().isEmpty()) {
                        lastLink = unescape(lm.group(1));
                        lastTitle = unescape(lm.group(2).trim());
                    }
                    Matcher sm = SNIPPET_PAT.matcher(row);
                    if (sm.find() && lastTitle != null) {
                        String snippet = unescape(sm.group(1).replaceAll("<[^>]+>", "").trim());
                        results.add(new WebSearchItem(lastTitle, snippet, lastLink));
                        lastTitle = null;
                        lastLink = null;
                        if (results.size() >= MAX_RESULTS) break;
                    }
                }
                searchCount++;
                if (results.isEmpty() && searchCount < 3) {
                    log.info("DDG '{}' got 0 results, retrying ({}/3)...", query, searchCount);
                    Thread.sleep(5000);
                }
            }
            log.info("DDG '{}' returned {} results", query, results.size());
            log.debug("DDG results:\n{}", results.stream()
                .map(r -> String.format("  [%s] %s", r.title(), r.url()))
                .reduce("", (a, b) -> a + b + "\n"));
        } catch (Exception e) {
            log.warn("DDG search failed '{}': {}", query, e.getMessage());
        }
        return results;
    }

    /**
     * 抓取 URL 并移除 HTML 标签与常见噪音内容。
     *
     * <p>返回文本截断为最多 3000 字符。</p>
     *
     * @param url 目标链接
     * @return 清洗后的正文文本
     */
    @Override
    public String fetch(String url) {
        try {
            String html = restTemplate.getForObject(url, String.class);
            if (html == null) return null;
            String text = html
                    .replaceAll("(?s)<script[^>]*>.*?</script>", " ")
                    .replaceAll("(?s)<style[^>]*>.*?</style>", " ")
                    .replaceAll("<[^>]+>", " ")
                    .replaceAll("&[a-z]+;", " ")
                    .replaceAll("\\s+", " ").trim();
            log.info("DDGFetch {} chars from {}", text.length(), url);
            return text.length() <= 3000 ? text : text.substring(0, 3000);
        } catch (Exception e) {
            log.warn("DDGFetch failed '{}': {}", url, e.getMessage());
            return null;
        }
    }

    /**
     * HTML 字符转义处理，复用于标题与链接字段。
     *
     * @param s 原始字符串
     * @return 转义后的字符串
     */
    private String unescape(String s) {
        return s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#x27;", "'").replace("&#39;", "'");
    }
}
