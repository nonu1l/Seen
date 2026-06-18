package com.nonu1l.media.agent.tool;

import com.nonu1l.media.model.dto.FetchUrlResultDTO;
import com.nonu1l.media.model.dto.FetchToolResultDTO;
import com.nonu1l.media.model.dto.WebSearchItemDTO;
import com.nonu1l.media.model.dto.WebSearchToolResultDTO;
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
    public List<WebSearchItemDTO> searchWeb(String query) {
        log.debug("Tool: searchWeb query='{}'", query);
        return webSearchService.search(query);
    }

    /**
     * 走统一 Web 搜索入口，并返回 Agent 可见的失败诊断。
     *
     * @param query 检索关键词
     * @return 搜索结构化结果
     */
    public WebSearchToolResultDTO searchWebForAgent(String query) {
        log.debug("Tool: searchWeb query='{}'", query);
        return webSearchService.searchWithDiagnostics(query);
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
    public FetchToolResultDTO fetchWeb(String url, String purpose, Integer maxChars) {
        log.debug("Tool: fetchWeb url='{}' purpose='{}'", url, purpose);
        return toToolResult(webFetchService.fetch(url, maxChars));
    }

    /**
     * 抓取任意公开 HTTP(S) URL，并返回结构化状态与正文，供内部搜索流水线使用。
     *
     * @param url 要抓取的 URL
     * @param purpose 抓取目的，便于日志和模型自我约束
     * @param maxChars 最大返回字符数
     * @return URL 抓取结果
     */
    public FetchUrlResultDTO fetchUrlRaw(String url, String purpose, Integer maxChars) {
        log.debug("Internal: fetchUrlRaw url='{}' purpose='{}'", url, purpose);
        return webFetchService.fetch(url, maxChars);
    }

    private FetchToolResultDTO toToolResult(FetchUrlResultDTO result) {
        boolean ok = result != null && result.error() == null && result.text() != null && !result.text().isBlank();
        String error = result == null ? "fetch returned null" : result.error();
        String hint = ok ? null : "可以换一个公开资料源，或先调用 searchWeb 查找可访问页面。";
        return new FetchToolResultDTO(
                ok,
                result != null ? result.url() : null,
                result != null ? result.status() : 0,
                result != null ? result.contentType() : "",
                result != null ? result.title() : "",
                result != null ? result.text() : "",
                result != null && result.truncated(),
                error,
                hint
        );
    }
}
