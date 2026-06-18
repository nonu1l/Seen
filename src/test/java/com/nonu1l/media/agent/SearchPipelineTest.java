package com.nonu1l.media.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 搜索流水线标题提取输出的防御性清洗测试。
 */
class SearchPipelineTest {

    @Test
    void cleanExtractedTitlesDropsNoMatchExplanationAndUnrelatedTitles() {
        String output = """
                抱歉，您提供的网页内容中并未包含与您查询意图相匹配的动漫列表。
                该网页内容主要包含：
                - **MyAnimeList 的"异世界动漫"分类页面**：但页面只显示了筛选器。
                从 AniList 区域可以提取到的标题（但与您的查询方向不匹配）：
                Re:Zero kara Hajimeru Isekai Seikatsu 4th Season
                Tensei Shitara Slime Datta Ken 4th Season
                """;

        assertTrue(SearchPipeline.cleanExtractedTitles(output).isEmpty());
    }

    @Test
    void cleanExtractedTitlesKeepsLikelyTitlesAndStripsListMarkers() {
        String output = """
                - Sword Art Online
                1. Log Horizon
                **Re:Zero kara Hajimeru Isekai Seikatsu**
                该网页内容主要包含：
                """;

        assertEquals(
                java.util.List.of("Sword Art Online", "Log Horizon", "Re:Zero kara Hajimeru Isekai Seikatsu"),
                SearchPipeline.cleanExtractedTitles(output));
    }

    @Test
    void cleanExtractedTitlesHonorsNoTitlesProtocol() {
        assertTrue(SearchPipeline.cleanExtractedTitles("NO_TITLES").isEmpty());
    }
}
