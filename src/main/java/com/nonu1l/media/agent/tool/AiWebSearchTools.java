package com.nonu1l.media.agent.tool;

import com.nonu1l.media.model.dto.WebFetchResultDTO;
import com.nonu1l.media.model.dto.WebSearchItemDTO;
import com.nonu1l.media.model.dto.WebSearchResultDTO;
import com.nonu1l.media.service.WebFetchService;
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
    private final WebFetchService webFetchService;

    /**
     * @param webSearchService Web 搜索聚合服务
     * @param webFetchService 独立 URL 抓取服务
     */
    public AiWebSearchTools(WebSearchService webSearchService, WebFetchService webFetchService) {
        this.webSearchService = webSearchService;
        this.webFetchService = webFetchService;
    }

    /**
     * 走统一 Web 搜索入口进行外部检索。
     *
     * @param query 检索关键词
     * @return 外部检索结果
     */
    public WebSearchResultDTO searchWeb(String query) {
        log.debug("Tool: searchWeb query='{}'", query);
        return webSearchService.searchWithDiagnostics(query);
    }

    /**
     * 走统一 Web 搜索入口，并只返回搜索结果条目供内部流水线使用。
     *
     * @param query 检索关键词
     * @return 外部检索结果条目
     */
    public List<WebSearchItemDTO> searchWebItems(String query) {
        log.debug("Internal: searchWebItems query='{}'", query);
        return webSearchService.searchWithDiagnostics(query).items();
    }

    /**
     * 抓取 Web 页面正文文本，供内部搜索流水线使用。
     *
     * @param url 要抓取的 URL
     * @return 清洗后的文本片段，失败时可能为 null
     */
    public String fetchWebText(String url) {
        log.debug("Internal: fetchWebText url='{}'", url);
        return webFetchService.fetchText(url);
    }

    /**
     * 抓取任意公开 HTTP(S) URL，并返回 Agent 可见的失败诊断。
     *
     * @param url 要抓取的 URL
     * @param purpose 抓取目的
     * @param maxChars 最大返回字符数
     * @return 抓取结构化结果
     */
    public WebFetchResultDTO fetchWeb(String url, String purpose, Integer maxChars) {
        log.debug("Tool: fetchWeb url='{}' purpose='{}'", url, purpose);
        return webFetchService.fetch(url, maxChars);
    }
}
