package com.nonu1l.media.service;

import com.nonu1l.media.model.dto.CompactSubject;
import com.nonu1l.media.model.dto.WorkSearchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class SearchResultPreprocessorTest {

    private final SearchResultPreprocessor preprocessor = new SearchResultPreprocessor();

    // ═══════════════════════════════════════════════════════
    // 噪声过滤 — 精确匹配
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("精确匹配 — 不应被过滤")
    class ExactMatch {

        @Test
        @DisplayName("名称以关键词开头")
        void shouldNotFilterPrefixMatch() {
            WorkSearchResult r = makeResult(283L, "南家三姐妹", "2007-10-07", 13, 7.6, 2);
            assertFalse(preprocessor.isNoise(r, "南家三姐妹"));
        }

        @Test
        @DisplayName("名称以关键词开头（带后缀）")
        void shouldNotFilterPrefixMatchWithSuffix() {
            WorkSearchResult r = makeResult(890L, "南家三姐妹 再来一碗", "2008-01-06", 13, 7.0, 2);
            assertFalse(preprocessor.isNoise(r, "南家三姐妹"));
        }

        @Test
        @DisplayName("名称包含关键词")
        void shouldNotFilterContainedMatch() {
            WorkSearchResult r = makeResult(283L, "【TV】南家三姐妹 第一季", "2007-10-07", 13, 7.6, 2);
            assertFalse(preprocessor.isNoise(r, "南家三姐妹"));
        }
    }

    // ═══════════════════════════════════════════════════════
    // 噪声过滤 — 明确噪音
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("明确噪音 — 应被过滤（零公共字符）")
    class DefiniteNoise {

        @Test
        @DisplayName("进击的巨人 与 南家三姐妹 零公共字符 → 过滤")
        void shouldFilterZeroCommonChars() {
            WorkSearchResult r = makeResult(1L, "进击的巨人", "2013-04-06", 25, 8.5, 2);
            assertTrue(preprocessor.isNoise(r, "南家三姐妹"));
        }

        @Test
        @DisplayName("名称为 null → 过滤")
        void shouldFilterNullName() {
            WorkSearchResult r = new WorkSearchResult();
            r.setId(1L);
            assertTrue(preprocessor.isNoise(r, "南家三姐妹"));
        }

        @Test
        @DisplayName("超元气三姐妹 与 南家三姐妹 共享三/姐/妹 → 不过滤（交给 LLM 判断）")
        void shouldKeepSharedCharsForLlm() {
            WorkSearchResult r = makeResult(5658L, "超元气三姐妹", "2010-07-02", 14, 7.4, 2);
            // 公共字：三、姐、妹 = 3 → 不过滤，LLM 看到完整名称后能区分
            assertFalse(preprocessor.isNoise(r, "南家三姐妹"));
        }

        @Test
        @DisplayName("魔法姐妹露露特莉莉 与 南家三姐妹 共享姐/妹 → 不过滤（交给 LLM 判断）")
        void shouldKeepMagicSistersForLlm() {
            WorkSearchResult r = makeResult(501796L, "魔法姐妹露露特莉莉", "2026-04-05", 12, 6.5, 2);
            // 公共字：姐、妹 = 2 → 不过滤
            assertFalse(preprocessor.isNoise(r, "南家三姐妹"));
        }
    }

    // ═══════════════════════════════════════════════════════
    // 噪声过滤 — 模糊名称（用户记不清剧名）
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("模糊名称 — 用户记不清，缺字/错字")
    class FuzzyNames {

        @Test
        @DisplayName("南家姐妹 → 南家三姐妹（缺字）")
        void nanJiaSistersMissingChar() {
            WorkSearchResult r = makeResult(283L, "南家三姐妹", "2007-10-07", 13, 7.6, 2);
            // "南家姐妹"(4字) vs "南家三姐妹": 公共字=4, half=2, 4>=2 → NOT noise
            assertFalse(preprocessor.isNoise(r, "南家姐妹"));
        }

        @Test
        @DisplayName("复仇联盟 → 复仇者联盟（缺字）")
        void avengersMissingChar() {
            WorkSearchResult r = makeResult(100L, "复仇者联盟", "2012-05-04", 1, 7.5, 6);
            // "复仇联盟"(4字) vs "复仇者联盟": 公共字=4, half=2, 4>=2 → NOT noise
            assertFalse(preprocessor.isNoise(r, "复仇联盟"));
        }

        @Test
        @DisplayName("复仇联盟 → 复仇者联盟：终局之战（缺字+后缀）")
        void avengersMissingCharWithSuffix() {
            WorkSearchResult r = makeResult(200L, "复仇者联盟：终局之战", "2019-04-24", 1, 8.5, 6);
            // "复仇联盟"(4字) vs "复仇者联盟：终局之战"(9字):
            //   contains? No. common chars: 复仇联盟 = 4, half=2, 4>=2 → NOT noise
            assertFalse(preprocessor.isNoise(r, "复仇联盟"));
        }

        @Test
        @DisplayName("声之型 → 声之形（错字：型→形）")
        void silentVoiceWrongChar() {
            WorkSearchResult r = makeResult(300L, "声之形", "2016-09-17", 1, 8.0, 6);
            // "声之型"(3字) vs "声之形": 公共字=声之=2, half=1.5, 2>=1.5 → NOT noise
            assertFalse(preprocessor.isNoise(r, "声之型"));
        }

        @Test
        @DisplayName("豪医生 → 豪斯医生（缺字）")
        void houseDoctorMissingChar() {
            WorkSearchResult r = makeResult(400L, "豪斯医生", "2004-11-16", 22, 9.0, 6);
            // "豪医生"(3字) vs "豪斯医生": 公共字=豪医生=3, half=1.5, 3>=1.5 → NOT noise
            assertFalse(preprocessor.isNoise(r, "豪医生"));
        }

        @Test
        @DisplayName("血战C → BLOOD-C（中文俗称→英文名，共享'C' → 不过滤）")
        void bloodCChineseToEnglish() {
            WorkSearchResult r = new WorkSearchResult();
            r.setId(500L);
            r.setNameCn(null);
            r.setNameOrig("BLOOD-C");
            r.setAirDate("2011-07-07");
            r.setEpsCount(12);
            r.setScore(7.0);
            r.setSubjectType(2);
            // "血战C"(3字) vs "BLOOD-C": common='C'=1 ≥ 1 → NOT noise
            assertFalse(preprocessor.isNoise(r, "血战C"));
        }

        @Test
        @DisplayName("血战C → BLOOD-C（中文名在 nameCn 中）")
        void bloodCChineseNameAvailable() {
            // 如果 bangumi 返回了中文 nameCn ="血战-C"
            WorkSearchResult r = new WorkSearchResult();
            r.setId(500L);
            r.setNameCn("血战-C");
            r.setNameOrig("BLOOD-C");
            r.setAirDate("2011-07-07");
            r.setEpsCount(12);
            r.setScore(7.0);
            r.setSubjectType(2);
            // coalesceName 优先取 nameCn="血战-C"
            // "血战C"(3字) vs "血战-C":
            //   startsWith("血战C")? No ("血战-C")
            //   contains("血战C")? No (有个"-")
            //   common chars: 血✓ 战✓ C✓ = 3, half=1.5, 3>=1.5 → NOT noise
            assertFalse(preprocessor.isNoise(r, "血战C"));
        }

        @Test
        @DisplayName("死神B → BLEACH（中文俗称→英文名）")
        void bleachChineseNickname() {
            WorkSearchResult r = new WorkSearchResult();
            r.setId(600L);
            r.setNameCn("死神");
            r.setNameOrig("BLEACH");
            r.setAirDate("2004-10-05");
            r.setEpsCount(366);
            r.setScore(7.9);
            r.setSubjectType(2);
            // "死神B"(3字) vs "死神": 公共字=死神=2, half=1.5, 2>=1.5 → NOT noise
            assertFalse(preprocessor.isNoise(r, "死神B"));
        }

        @Test
        @DisplayName("钢炼 → 钢之炼金术师（简称）")
        void fmaAbbreviation() {
            WorkSearchResult r = makeResult(700L, "钢之炼金术师 FULLMETAL ALCHEMIST",
                    "2009-04-05", 64, 9.2, 2);
            // "钢炼"(2字) vs "钢之炼金术师 FULLMETAL ALCHEMIST":
            //   common chars: 钢✓ 炼✓ = 2, half=1, 2>=1 → NOT noise
            assertFalse(preprocessor.isNoise(r, "钢炼"));
        }

        @Test
        @DisplayName("巨人 → 进击的巨人（简称，缺关键词主体）")
        void attackOnTitanShortName() {
            WorkSearchResult r = makeResult(800L, "进击的巨人", "2013-04-06", 25, 8.5, 2);
            // "巨人"(2字) vs "进击的巨人": contains("巨人")? YES → NOT noise
            assertFalse(preprocessor.isNoise(r, "巨人"));
        }

        @Test
        @DisplayName("未闻花名 → 我们仍未知道那天所看见的花的名字（俗称→全名）")
        void anohanaNickname() {
            WorkSearchResult r = makeResult(900L, "我们仍未知道那天所看见的花的名字",
                    "2011-04-14", 11, 8.2, 2);
            // "未闻花名"(4字) vs "我们仍未知道那天所看见的花的名字":
            //   common chars: 未✓ 闻✓ 花?✗ 名?✓ ... let me count:
            //   我们仍未知道那天所看见的花的名字
            //   chars of keyword "未闻花名": 未✓ 闻✓ 花✓ 名✓ = 4, half=2, 4>=2 → NOT noise
            assertFalse(preprocessor.isNoise(r, "未闻花名"));
        }
    }

    // ═══════════════════════════════════════════════════════
    // 条目分类
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("条目分类逻辑")
    class Classification {

        @Test
        @DisplayName("未上映 — 空日期")
        void shouldClassifyUnreleasedByEmptyDate() {
            WorkSearchResult r = makeResult(502928L, "南家三姐妹 从今以后", "", null, 8.3, 2);
            assertEquals("UNRELEASED", preprocessor.classify(r));
        }

        @Test
        @DisplayName("未上映 — 0000-00-00")
        void shouldClassifyUnreleasedByZeroDate() {
            WorkSearchResult r = makeResult(502928L, "南家三姐妹 从今以后", "0000-00-00", null, 8.3, 2);
            assertEquals("UNRELEASED", preprocessor.classify(r));
        }

        @Test
        @DisplayName("TV — 多集动画")
        void shouldClassifyTvByEpisodeCount() {
            WorkSearchResult r = makeResult(283L, "南家三姐妹", "2007-10-07", 13, 7.6, 2);
            assertEquals("TV", preprocessor.classify(r));
        }

        @Test
        @DisplayName("TV — 长集数")
        void shouldClassifyTvByLongEpisodeCount() {
            WorkSearchResult r = makeResult(800L, "海贼王", "1999-10-20", 1100, 8.8, 2);
            assertEquals("TV", preprocessor.classify(r));
        }

        @Test
        @DisplayName("OVA — 标签含 OVA")
        void shouldClassifyOvaByTags() {
            WorkSearchResult r = makeResult(3016L, "南家三姐妹 饭后甜点", "2009-06-23", 1, 7.3, 2);
            r.setTags(List.of("OVA", "日本", "漫画改"));
            assertEquals("OVA", preprocessor.classify(r));
        }

        @Test
        @DisplayName("OVA — 标签含 OAD")
        void shouldClassifyOvaByOadTag() {
            WorkSearchResult r = makeResult(47684L, "南家三姐妹 久候多时", "2012-10-05", 1, 7.3, 2);
            r.setTags(List.of("OAD", "日本"));
            assertEquals("OVA", preprocessor.classify(r));
        }

        @Test
        @DisplayName("OVA — 动画单集")
        void shouldClassifyOvaBySingleEpisode() {
            WorkSearchResult r = makeResult(47684L, "南家三姐妹 久候多时", "2012-10-05", 1, 7.3, 2);
            assertEquals("OVA", preprocessor.classify(r));
        }

        @Test
        @DisplayName("MOVIE — 标签含 剧场版")
        void shouldClassifyMovieByTags() {
            WorkSearchResult r = makeResult(300L, "声之形", "2016-09-17", 1, 8.0, 2);
            r.setTags(List.of("剧场版", "日本"));
            assertEquals("MOVIE", preprocessor.classify(r));
        }

        @Test
        @DisplayName("MOVIE — subjectType=6（真人）且单集")
        void shouldClassifyMovieBySubjectType() {
            WorkSearchResult r = makeResult(100L, "复仇者联盟：终局之战", "2019-04-24", 1, 8.5, 6);
            assertEquals("MOVIE", preprocessor.classify(r));
        }

        @Test
        @DisplayName("SPECIAL — 短集数")
        void shouldClassifySpecial() {
            WorkSearchResult r = makeResult(999L, "某个特别篇", "2020-01-01", 3, 6.0, 2);
            assertEquals("SPECIAL", preprocessor.classify(r));
        }
    }

    // ═══════════════════════════════════════════════════════
    // 排序
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("排序逻辑")
    class Sorting {

        @Test
        @DisplayName("按上映日期升序排列")
        void shouldSortByAirDateAsc() {
            WorkSearchResult s1 = makeResult(283L, "南家三姐妹", "2007-10-07", 13, 7.6, 2);
            WorkSearchResult s2 = makeResult(890L, "南家三姐妹 再来一碗", "2008-01-06", 13, 7.0, 2);
            WorkSearchResult s3 = makeResult(889L, "南家三姐妹 欢迎回来", "2009-01-04", 13, 7.2, 2);
            WorkSearchResult s4 = makeResult(47685L, "南家三姐妹 我回来了", "2013-01-05", 13, 7.6, 2);

            List<CompactSubject> result = preprocessor.preprocess(
                    List.of(s3, s1, s4, s2), "南家三姐妹");

            assertEquals(4, result.size());
            assertEquals(283L, result.get(0).id());
            assertEquals(890L, result.get(1).id());
            assertEquals(889L, result.get(2).id());
            assertEquals(47685L, result.get(3).id());
        }

        @Test
        @DisplayName("未上映条目排在最后")
        void shouldPutUnreleasedLast() {
            WorkSearchResult s1 = makeResult(283L, "南家三姐妹", "2007-10-07", 13, 7.6, 2);
            WorkSearchResult unreleased = makeResult(502928L, "南家三姐妹 从今以后", "", null, 8.3, 2);

            List<CompactSubject> result = preprocessor.preprocess(
                    List.of(unreleased, s1), "南家三姐妹");

            assertEquals(2, result.size());
            assertEquals(283L, result.get(0).id());
            assertEquals(502928L, result.get(1).id());
        }

        @Test
        @DisplayName("同日期按 id 升序")
        void shouldSortByIdWhenSameDate() {
            WorkSearchResult a = makeResult(200L, "作品A", "2020-01-01", 12, 7.0, 2);
            WorkSearchResult b = makeResult(100L, "作品B", "2020-01-01", 12, 7.0, 2);

            List<CompactSubject> result = preprocessor.preprocess(
                    List.of(a, b), "作品");

            assertEquals(2, result.size());
            assertEquals(100L, result.get(0).id());
            assertEquals(200L, result.get(1).id());
        }
    }

    // ═══════════════════════════════════════════════════════
    // 紧凑文本格式化
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("紧凑文本格式化")
    class CompactText {

        @Test
        @DisplayName("完整字段格式化")
        void shouldFormatAllFields() {
            CompactSubject s = new CompactSubject(283L, "南家三姐妹", "TV",
                    "2007-10-07", 13, 7.6, 669);
            String line = s.toCompactLine();

            assertTrue(line.contains("[283]"));
            assertTrue(line.contains("南家三姐妹"));
            assertTrue(line.contains("TV"));
            assertTrue(line.contains("2007-10-07"));
            assertTrue(line.contains("13集"));
            assertTrue(line.contains("7.6"));
            assertTrue(line.contains("669"));
        }

        @Test
        @DisplayName("缺失字段用占位符")
        void shouldHandleNullFields() {
            CompactSubject s = new CompactSubject(283L, "测试", "TV",
                    null, null, null, null);
            String line = s.toCompactLine();

            assertTrue(line.contains("----"));
            assertTrue(line.contains("?集"));
            assertFalse(line.contains("评分"));
            assertFalse(line.contains("排名"));
        }

        @Test
        @DisplayName("Score=0 时不显示评分")
        void shouldHideZeroScore() {
            CompactSubject s = new CompactSubject(283L, "测试", "TV",
                    "2020-01-01", 12, 0.0, null);
            String line = s.toCompactLine();
            assertFalse(line.contains("评分"));
        }

        @Test
        @DisplayName("Rank=0 时不显示排名")
        void shouldHideZeroRank() {
            CompactSubject s = new CompactSubject(283L, "测试", "TV",
                    "2020-01-01", 12, 7.5, 0);
            String line = s.toCompactLine();
            assertFalse(line.contains("排名"));
        }

        @Test
        @DisplayName("toCompactText 包含关键词和规则")
        void shouldContainKeywordAndRules() {
            List<CompactSubject> subjects = List.of(
                    new CompactSubject(283L, "南家三姐妹", "TV", "2007-10-07", 13, 7.6, 669)
            );
            String text = preprocessor.toCompactText(subjects, "南家三姐妹", null);

            assertTrue(text.contains("搜索关键词：南家三姐妹"));
            assertTrue(text.contains("第N季"));
            assertTrue(text.contains("UNRELEASED"));
        }
    }

    // ═══════════════════════════════════════════════════════
    // 端到端预处理
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("端到端预处理流程")
    class EndToEnd {

        @Test
        @DisplayName("过滤零公共字符 + 分类 + 排序，全链路")
        void shouldFilterNoiseAndClassifyAll() {
            List<WorkSearchResult> raw = List.of(
                    makeResult(283L, "南家三姐妹", "2007-10-07", 13, 7.6, 2),
                    makeResult(890L, "南家三姐妹 再来一碗", "2008-01-06", 13, 7.0, 2),
                    makeResult(1L, "进击的巨人", "2013-04-06", 25, 8.5, 2),      // 零公共字符 → 过滤
                    makeResult(5658L, "超元气三姐妹", "2010-07-02", 14, 7.4, 2),  // 共享三/姐/妹 → 保留，LLM 判断
                    makeResult(47684L, "南家三姐妹 久候多时", "2012-10-05", 1, 7.3, 2),
                    makeResult(502928L, "南家三姐妹 从今以后", "", null, 8.3, 2)   // 未上映
            );

            List<CompactSubject> result = preprocessor.preprocess(raw, "南家三姐妹");

            // 进击的巨人 被过滤；超元气三姐妹 保留（有公共字符）
            assertEquals(5, result.size());
            assertTrue(result.stream().noneMatch(s -> s.id() == 1L));

            assertEquals("TV", result.get(0).category());
            assertEquals("TV", result.get(1).category());
            assertEquals("TV", result.get(2).category());    // 超元气三姐妹
            assertEquals("OVA", result.get(3).category());
            assertEquals("UNRELEASED", result.get(4).category());
        }

        @Test
        @DisplayName("空输入返回空列表")
        void shouldReturnEmptyForNullInput() {
            assertTrue(preprocessor.preprocess(null, "test").isEmpty());
        }

        @Test
        @DisplayName("空列表返回空列表")
        void shouldReturnEmptyForEmptyList() {
            assertTrue(preprocessor.preprocess(List.of(), "test").isEmpty());
        }

        @Test
        @DisplayName("id 为 null 的条目跳过")
        void shouldSkipNullIdEntries() {
            WorkSearchResult noId = new WorkSearchResult();
            noId.setNameCn("测试");
            noId.setAirDate("2020-01-01");

            List<CompactSubject> result = preprocessor.preprocess(List.of(noId), "测试");
            assertTrue(result.isEmpty());
        }
    }

    // ═══════════════════════════════════════════════════════
    // 更多影视作品测试用例
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("各类影视作品匹配")
    class VariousWorks {

        @Test
        @DisplayName("TV动画 → 间谍过家家")
        void spyFamily() {
            WorkSearchResult r = makeResult(1001L, "间谍过家家", "2022-04-09", 25, 8.2, 2);
            assertFalse(preprocessor.isNoise(r, "间谍过家家"));
            assertEquals("TV", preprocessor.classify(r));
        }

        @Test
        @DisplayName("TV动画简称 → 间谍过家家 → 间谍")
        void spyFamilyAbbreviation() {
            WorkSearchResult r = makeResult(1001L, "间谍过家家", "2022-04-09", 25, 8.2, 2);
            // "间谍"(2字) vs "间谍过家家": contains? YES → NOT noise
            assertFalse(preprocessor.isNoise(r, "间谍"));
        }

        @Test
        @DisplayName("电影 → 你的名字")
        void yourName() {
            WorkSearchResult r = new WorkSearchResult();
            r.setId(1002L);
            r.setNameCn("你的名字。");
            r.setAirDate("2016-08-26");
            r.setEpsCount(1);
            r.setScore(8.6);
            r.setSubjectType(2);
            r.setTags(List.of("剧场版", "日本"));
            assertFalse(preprocessor.isNoise(r, "你的名字"));
            assertEquals("MOVIE", preprocessor.classify(r));
        }

        @Test
        @DisplayName("电视剧 → 权力的游戏")
        void gameOfThrones() {
            WorkSearchResult r = makeResult(1003L, "权力的游戏", "2011-04-17", 73, 9.0, 6);
            assertFalse(preprocessor.isNoise(r, "权力的游戏"));
            assertEquals("TV", preprocessor.classify(r));
        }

        @Test
        @DisplayName("OVA → 机动战士高达 第08MS小队")
        void gundamOva() {
            WorkSearchResult r = makeResult(1004L, "机动战士高达 第08MS小队",
                    "1996-01-25", 12, 8.5, 2);
            r.setTags(List.of("OVA", "日本", "机器人"));
            assertFalse(preprocessor.isNoise(r, "机动战士高达"));
            assertEquals("OVA", preprocessor.classify(r));
        }

        @Test
        @DisplayName("未上映/未定档 → 标注 UNRELEASED")
        void upcomingAnime() {
            WorkSearchResult r = makeResult(1005L, "某部未定档新番", "", null, null, 2);
            assertEquals("UNRELEASED", preprocessor.classify(r));
        }

        @Test
        @DisplayName("电影匹配 — 最后一部已上映（排除0000日期）")
        void movieMatchLastReleased() {
            WorkSearchResult m1 = makeResult(2001L, "复仇者联盟", "2012-05-04", 1, 7.5, 6);
            WorkSearchResult m2 = makeResult(2002L, "复仇者联盟2：奥创纪元", "2015-05-01", 1, 7.0, 6);
            WorkSearchResult m3 = makeResult(2003L, "复仇者联盟3：无限战争", "2018-04-27", 1, 8.0, 6);
            WorkSearchResult m4 = makeResult(2004L, "复仇者联盟4：终局之战", "2019-04-24", 1, 8.5, 6);
            WorkSearchResult unreleased = makeResult(2005L, "复仇者联盟：秘密战争", "0000-00-00", null, null, 6);

            List<CompactSubject> result = preprocessor.preprocess(
                    List.of(m2, unreleased, m1, m4, m3), "复仇者联盟");

            // 已上映的按日期排序，未上映的排最后
            assertEquals(5, result.size());
            assertEquals("复仇者联盟", result.get(0).nameCn());                    // 2012
            assertEquals("复仇者联盟2：奥创纪元", result.get(1).nameCn());           // 2015
            assertEquals("复仇者联盟3：无限战争", result.get(2).nameCn());           // 2018
            assertEquals("复仇者联盟4：终局之战", result.get(3).nameCn());           // 2019 ← 最后一部已上映
            assertEquals("UNRELEASED", result.get(4).category());                  // 未上映排最后
        }

        @Test
        @DisplayName("系列剧 — 第一季不带季号后缀")
        void seriesFirstSeasonNoSuffix() {
            WorkSearchResult s1 = makeResult(283L, "南家三姐妹", "2007-10-07", 13, 7.6, 2);
            WorkSearchResult s2 = makeResult(890L, "南家三姐妹 再来一碗", "2008-01-06", 13, 7.0, 2);

            List<CompactSubject> result = preprocessor.preprocess(
                    List.of(s2, s1), "南家三姐妹");

            assertEquals(2, result.size());
            // 第一季不带季号名称
            assertEquals("南家三姐妹", result.get(0).nameCn());
            assertFalse(result.get(0).nameCn().contains("第一季"));
        }
    }

    // ═══════════════════════════════════════════════════════
    // helper
    // ═══════════════════════════════════════════════════════

    private WorkSearchResult makeResult(Long id, String nameCn, String airDate,
                                         Integer epsCount, Double score, Integer subjectType) {
        WorkSearchResult r = new WorkSearchResult();
        r.setId(id);
        r.setNameCn(nameCn);
        r.setAirDate(airDate);
        r.setEpsCount(epsCount);
        r.setScore(score);
        r.setSubjectType(subjectType);
        return r;
    }
}
