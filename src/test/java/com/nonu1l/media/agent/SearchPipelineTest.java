package com.nonu1l.media.agent;

import com.nonu1l.media.model.dto.WebSearchItemDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SearchPipelineTest {

    @Test
    void fallbackTitlesFromFetchedDataExtractsWrappedTitles() {
        SearchPipeline pipeline = pipeline();
        List<String> pageTexts = List.of("""
                2026 上半年动画推荐：《葬送的芙莉莲 第二季》、《咒术回战 死灭回游前篇》、
                《我推的孩子 第三季》都值得关注。
                """);
        List<WebSearchItemDTO> webResults = List.of(
                new WebSearchItemDTO(
                        "2026年冬季新番10大强档动漫推荐｜《鬼灭之刃 无限城篇》",
                        "本季也包含《排球少年 FINAL》和《无职转生 第三季》。",
                        "https://example.com/anime")
        );

        List<String> titles = pipeline.fallbackTitlesFromFetchedData(pageTexts, webResults);

        assertThat(titles).containsExactly(
                "葬送的芙莉莲 第二季",
                "咒术回战 死灭回游前篇",
                "我推的孩子 第三季",
                "鬼灭之刃 无限城篇",
                "排球少年 FINAL",
                "无职转生 第三季"
        );
    }

    @Test
    void fallbackTitlesFromFetchedDataFiltersGenericWrappedText() {
        SearchPipeline pipeline = pipeline();
        List<String> pageTexts = List.of("页面里有《2026年热门动画推荐》和《真正的作品名》。");

        List<String> titles = pipeline.fallbackTitlesFromFetchedData(pageTexts, List.of());

        assertThat(titles).containsExactly("真正的作品名");
    }

    private SearchPipeline pipeline() {
        return new SearchPipeline(null, null, null, 5, 10, 3, 3, 3, 6000, 8000);
    }
}
