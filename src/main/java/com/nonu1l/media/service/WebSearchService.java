package com.nonu1l.media.service;

import com.nonu1l.media.model.dto.WebSearchItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

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
        return delegate.fetch(url);
    }
}
