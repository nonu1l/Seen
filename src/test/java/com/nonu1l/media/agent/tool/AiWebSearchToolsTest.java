package com.nonu1l.media.agent.tool;

import com.nonu1l.media.model.dto.FetchUrlResultDTO;
import com.nonu1l.media.model.dto.WebSearchToolResultDTO;
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
                .thenReturn(new FetchUrlResultDTO("https://example.com/slow", 0, "", "", "", false,
                        "Connect timed out"));

        AiWebSearchTools tools = new AiWebSearchTools(searchService, fetchService);
        var result = tools.fetchWeb("https://example.com/slow", "debug", 1000);

        assertFalse(result.ok());
        assertEquals("Connect timed out", result.error());
    }

    @Test
    void searchWebForAgentReturnsSearchDiagnostics() {
        WebSearchService searchService = mock(WebSearchService.class);
        WebFetchService fetchService = mock(WebFetchService.class);
        when(searchService.searchWithDiagnostics("no result"))
                .thenReturn(new WebSearchToolResultDTO(false, "no result", "ddg", 0, List.of(),
                        "DuckDuckGo returned 0 results after 3 attempts", "换关键词"));

        AiWebSearchTools tools = new AiWebSearchTools(searchService, fetchService);
        var result = tools.searchWebForAgent("no result");

        assertFalse(result.ok());
        assertEquals("ddg", result.provider());
        assertEquals("DuckDuckGo returned 0 results after 3 attempts", result.error());
    }
}
