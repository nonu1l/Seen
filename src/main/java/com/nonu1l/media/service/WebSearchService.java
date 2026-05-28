package com.nonu1l.media.service;

import com.nonu1l.media.model.dto.WebSearchItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DuckDuckGo 网页搜索（Lite 版本，免费免 key）。
 */
@Service
public class WebSearchService {

    private static final Logger log = LoggerFactory.getLogger(WebSearchService.class);
    private static final String URL = "https://lite.duckduckgo.com/lite/";
    private static final int MAX_RESULTS = 10;

    private static final Pattern LINK_PAT = Pattern.compile("<a[^>]*href=\"([^\"]*)\"[^>]*>([^<]*)");
    private static final Pattern SNIPPET_PAT = Pattern.compile("class=\"result-snippet\"[^>]*>(.+?)</td>");

    private final RestTemplate restTemplate;

    public WebSearchService(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(8))
                .build();
    }

    public List<WebSearchItem> search(String query) {
        List<WebSearchItem> results = new ArrayList<>();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");

            String html = restTemplate.getForObject(
                    URL + "?q=" + java.net.URLEncoder.encode(query, "UTF-8"),
                    String.class);

            if (html == null) return results;

            // 解析 HTML
            String[] rows = html.split("<tr[ >]");
            String lastLink = null, lastTitle = null;

            for (String row : rows) {
                // 提取链接
                Matcher lm = LINK_PAT.matcher(row);
                if (lm.find() && !lm.group(2).trim().isEmpty()) {
                    lastLink = unescape(lm.group(1));
                    lastTitle = unescape(lm.group(2).trim());
                }

                // 提取摘要
                Matcher sm = SNIPPET_PAT.matcher(row);
                if (sm.find() && lastTitle != null) {
                    String snippet = unescape(sm.group(1).replaceAll("<[^>]+>", "").trim());
                    results.add(new WebSearchItem(lastTitle, snippet, lastLink));
                    lastTitle = null;
                    lastLink = null;
                    if (results.size() >= MAX_RESULTS) break;
                }
            }

            log.debug("WebSearch '{}' found {} results", query, results.size());
        } catch (Exception e) {
            log.warn("WebSearch failed query='{}': {}", query, e.getMessage());
        }
        return results;
    }

    private String unescape(String s) {
        return s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#x27;", "'").replace("&#39;", "'");
    }
}
