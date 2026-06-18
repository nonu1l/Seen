package com.nonu1l.media.agent.tool;

import com.nonu1l.media.model.dto.WebFetchResultDTO;
import com.nonu1l.media.model.dto.WebSearchResultDTO;
import com.nonu1l.media.service.WebFetchService;
import com.nonu1l.media.service.WebSearchService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AI Web 工具结构化失败返回测试。
 */
class AiWebSearchToolsTest {

    @Test
    void fetchWebReturnsErrorResultWhenFetchFails() {
        WebSearchService searchService = mock(WebSearchService.class);
        WebFetchService fetchService = mock(WebFetchService.class);
        when(fetchService.fetch("https://example.com/slow", 1000))
                .thenReturn(new WebFetchResultDTO(false, "https://example.com/slow", 0, "", "", "", false,
                        "Connect timed out", "可以换一个公开资料源。"));

        AiWebSearchTools tools = new AiWebSearchTools(searchService, fetchService);
        var result = tools.fetchWeb("https://example.com/slow", "debug", 1000);

        assertFalse(result.ok());
        assertEquals("Connect timed out", result.error());
    }

    @Test
    void searchWebReturnsSearchDiagnostics() {
        WebSearchService searchService = mock(WebSearchService.class);
        WebFetchService fetchService = mock(WebFetchService.class);
        when(searchService.searchWithDiagnostics("no result"))
                .thenReturn(new WebSearchResultDTO(false, "no result", "tavily", 0, List.of(),
                        "Tavily returned 0 usable results", "换关键词"));

        AiWebSearchTools tools = new AiWebSearchTools(searchService, fetchService);
        var result = tools.searchWeb("no result");

        assertFalse(result.ok());
        assertEquals("tavily", result.provider());
        assertEquals("Tavily returned 0 usable results", result.error());
    }
}
