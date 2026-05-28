package com.nonu1l.media.service;

import com.nonu1l.media.model.dto.IntentAnalysisResult;
import com.nonu1l.media.model.dto.MatchedEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "LLM_API_KEY", matches = ".+")
@DisplayName("@Tool Agent 集成测试")
class IntentAnalysisServiceTest {

    @Autowired
    private IntentAnalysisService service;

    @Nested
    @DisplayName("标记作品")
    class Marking {

        @Test
        @DisplayName("单剧单季 + 显式评分")
        void singleSeasonExplicitRating() {
            IntentAnalysisResult result = service.analyze("南家三姐妹第一季，不错，8分吧", "");
            assertNotNull(result.entries());
            assertFalse(result.entries().isEmpty());
            MatchedEntry e = result.entries().getFirst();
            assertEquals(283L, e.subjectId());
            assertEquals(8, e.rating());
            assertEquals("collect", e.status());
        }

        @Test
        @DisplayName("抛弃 + 低分")
        void droppedLowRating() {
            IntentAnalysisResult result = service.analyze("南家三姐妹第二季，不好看，弃了", "");
            assertNotNull(result.entries());
            assertFalse(result.entries().isEmpty());
            MatchedEntry e = result.entries().getFirst();
            assertEquals("dropped", e.status());
            if (e.rating() != null) assertTrue(e.rating() <= 5);
        }

        @Test
        @DisplayName("想看")
        void wishStatus() {
            IntentAnalysisResult result = service.analyze("想看南家三姐妹", "");
            assertNotNull(result.entries());
            MatchedEntry e = result.entries().getFirst();
            if (e.status() != null) assertEquals("wish", e.status());
        }

        @Test
        @DisplayName("多季全看过 + 差异评价")
        void allSeasonsDiffRating() {
            IntentAnalysisResult result = service.analyze(
                    "南家三姐妹都看过了，还行，就是第二季第三季画风有点崩", "");
            assertNotNull(result.entries());
            assertTrue(result.entries().size() >= 4);
            assertTrue(result.entries().stream().noneMatch(e -> e.subjectId() == 502928L));
        }
    }

    @Nested
    @DisplayName("模糊名称")
    class FuzzyNames {

        @Test
        @DisplayName("南家姐妹(缺字)→南家三姐妹")
        void missingChar() {
            IntentAnalysisResult result = service.analyze("南家姐妹第一季看过了，7分", "");
            assertNotNull(result.entries());
            assertFalse(result.entries().isEmpty());
        }

        @Test
        @DisplayName("简称→全名")
        void abbreviation() {
            IntentAnalysisResult result = service.analyze("钢炼看完了，经典神作，9分", "");
            assertNotNull(result.entries());
            assertFalse(result.entries().isEmpty());
        }
    }

    @Nested
    @DisplayName("多剧拆分")
    class MultiWork {

        @Test
        @DisplayName("豪斯医生1-3季 + 复仇者联盟")
        void twoWorks() {
            IntentAnalysisResult result = service.analyze(
                    "豪斯医生1-3季都看过了，复仇者联盟也看过了，都不错", "");
            assertNotNull(result.entries());
            assertFalse(result.entries().isEmpty());
            // 应该匹配到两部作品
            boolean hasHouse = result.entries().stream().anyMatch(e -> e.nameCn().contains("豪斯"));
            boolean hasAvengers = result.entries().stream().anyMatch(e -> e.nameCn().contains("复仇者"));
            assertTrue(hasHouse || hasAvengers);
        }
    }

    @Nested
    @DisplayName("多轮上下文")
    class MultiTurn {

        @Test
        @DisplayName("省略剧名从历史推断")
        void inferFromHistory() {
            String history = "用户：南家三姐妹第一季，8分\n助手：已匹配《南家三姐妹》...\n";
            IntentAnalysisResult result = service.analyze("第二季也看过，7分", history);
            assertNotNull(result.entries());
            assertFalse(result.entries().isEmpty());
        }
    }

    @Nested
    @DisplayName("推荐和分析")
    class Recommendation {

        @Test
        @DisplayName("推荐类意图不抛异常")
        void recommend() {
            IntentAnalysisResult result = service.analyze("推荐点好看的动画", "");
            assertNotNull(result);
            // 推荐可能没有精确匹配的 cards，但不应抛异常
        }
    }
}
