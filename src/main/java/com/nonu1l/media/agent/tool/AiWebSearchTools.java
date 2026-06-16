package com.nonu1l.media.agent.tool;

import com.nonu1l.media.model.dto.WebSearchItem;
import com.nonu1l.media.service.WebSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI 专用外部网页搜索与抓取工具。
 */
@Component
public class AiWebSearchTools {

    private static final Logger log = LoggerFactory.getLogger(AiWebSearchTools.class);

    private final WebSearchService webSearchService;

    /**
     * @param webSearchService Web 搜索聚合服务
     */
    public AiWebSearchTools(WebSearchService webSearchService) {
        this.webSearchService = webSearchService;
    }

    /**
     * 走统一 Web 搜索入口进行外部检索。
     *
     * @param query 检索关键词
     * @return 外部检索结果
     */
    public List<WebSearchItem> searchWeb(String query) {
        log.debug("Tool: searchWeb query='{}'", query);
        return webSearchService.search(query);
    }

    /**
     * 抓取 Web 页面正文文本。
     *
     * @param url 要抓取的 URL
     * @return 清洗后的文本片段，失败时可能为 null
     */
    public String fetchWeb(String url) {
        log.debug("Tool: fetchWeb url='{}'", url);
        return webSearchService.fetch(url);
    }
}
