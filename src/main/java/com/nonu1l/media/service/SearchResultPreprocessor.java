package com.nonu1l.media.service;

import com.nonu1l.media.model.dto.CompactSubject;
import com.nonu1l.media.model.dto.StructuredIntent;
import com.nonu1l.media.model.dto.WorkSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Bangumi 搜索结果预处理 — 纯 Java，零 token 消耗。
 * 负责：噪音过滤、条目分类、按日期排序、紧凑文本格式化。
 */
@Component
public class SearchResultPreprocessor {

    private static final Logger log = LoggerFactory.getLogger(SearchResultPreprocessor.class);

    /**
     * 对搜索结果进行过滤、分类
     *
     * @param rawResults Bangumi 原始搜索结果
     * @param keyword    用户搜索关键词
     * @return 预处理后的精简条目列表
     */
    public List<CompactSubject> preprocess(List<WorkSearchResult> rawResults, String keyword) {
        if (rawResults == null || rawResults.isEmpty()) return List.of();

        List<CompactSubject> subjects = new ArrayList<>();
        for (WorkSearchResult r : rawResults) {
            if (r.getId() == null) continue;
//            if (isNoise(r, keyword)) {
//                log.debug("Filtered noise: id={} nameCn={}", r.getId(), r.getNameCn());
//                continue;
//            }
            String category = classify(r);
            subjects.add(new CompactSubject(
                    r.getId(),
                    coalesceName(r),
                    category,
                    normalizeDate(r.getAirDate()),
                    r.getEpsCount(),
                    r.getScore(),
                    r.getRank()
            ));
        }


        /**
         * 暂时去除排序，后续应该使用llm来分析识别出最新一季，未上映那一部等操作
         *
         */

//        // 按 rank 升序（高排名优先），无排名排最后；同 rank 按日期升序
//        subjects.sort(Comparator
//                .comparing(CompactSubject::rank, Comparator.nullsLast(
//                        Comparator.naturalOrder()))
//                .thenComparing(CompactSubject::airDate, Comparator.nullsLast(
//                        Comparator.naturalOrder()))
//                .thenComparing(CompactSubject::id));
//
        return subjects;
    }

    /**
     * 将 CompactSubject 列表格式化为 LLM 友好的紧凑文本。
     */
    public String toCompactText(List<CompactSubject> subjects, String keyword, StructuredIntent intent) {
        StringBuilder sb = new StringBuilder();
        sb.append("搜索关键词：").append(keyword).append("\n");
        if (intent != null) {
            sb.append("用户意图：");
            List<String> parts = new ArrayList<>();
            if (intent.season() != null) {
                parts.add(intent.season() == -1 ? "最终季" : "第" + intent.season() + "季");
            }
            if (intent.rating() != null) parts.add("评分" + intent.rating() + "分");
            if (intent.comment() != null) parts.add("评价: " + intent.comment());
            if (intent.scope() != null) {
                String scopeLabel = switch (intent.scope()) {
                    case "all" -> "全系列";
                    case "range" -> "指定范围";
                    case "single" -> "单季";
                    default -> intent.scope();
                };
                parts.add("范围: " + scopeLabel);
            }
            if (intent.status() != null) parts.add("状态: " + intent.status());
            sb.append(String.join(" | ", parts)).append("\n");
        }
        sb.append("\n搜索结果（按上映日期排序）：\n");
        for (CompactSubject s : subjects) {
            sb.append(s.toCompactLine()).append("\n");
        }
        sb.append("\n规则：\n");
        sb.append("- 搜索结果已按相关性排序（排名高+评分高的优先），排名高的条目更可能是用户要找的作品\n");
        sb.append("- 只匹配名称与关键词明显相关的条目。如果条目名称差异过大（如乐高、LEGO、衍生动画），不应匹配\n");
        sb.append("- 第N季 = 按相关性排序后第N个TV条目（跳过OVA和UNRELEASED）\n");
        sb.append("- scope=range 且评论含\"涵盖第1-3季\"→匹配第1到第3个TV条目\n");
        sb.append("- UNRELEASED 条目不能标记为\"看过\"或\"在看\"，只能标记为\"想看\"\n");
        sb.append("- \"都看过了\"(scope=all) = 匹配所有已上映且名称相关的TV条目，不匹配衍生/乐高/动画改编\n");
        sb.append("- 电影优先匹配排名高评分高的，\"最后一部\" = 已上映MOVIE中排名最高且日期最新的一条\n");
        sb.append("- 评价中提到的特定季数需对应降分/升分\n");
        sb.append("- 若用户未指定评分，根据情感推断：\"不错\"\"好看\"→7，\"还行\"\"一般\"→6，\"很好看\"\"感动\"\"精彩\"→8-9\n");
        return sb.toString();
    }

    /**
     * 判断是否为搜索噪音 — 名称与关键词无关的条目。
     */
    public boolean isNoise(WorkSearchResult item, String keyword) {
//        if (keyword == null || keyword.isBlank()) return false;
//        String name = coalesceName(item);
//        if (name == null) return true;
//
//        // 名称以关键词开头 → 相关
//        if (name.startsWith(keyword)) return false;
//
//        // 名称包含关键词 → 相关
//        if (name.contains(keyword)) return false;
//
//        // 与关键词无任何公共字符 → 噪音
//        // 阈值设为 1 而非 keyword.length()/2，避免中文俗称→英文名
//        // 的场景（如"血战C"→"BLOOD-C"只有'C'匹配）被误过滤。
//        // 有公共字符的结果交给 LLM Phase 2 做最终语义判断。
//        for (int i = 0; i < name.length(); i++) {
//            if (keyword.indexOf(name.toUpperCase().charAt(i)) >= 0) return false;
//        }


        //去除噪声过滤
        return false;
    }

    /**
     * 条目分类：TV / OVA / MOVIE / UNRELEASED / SPECIAL。
     */
    public String classify(WorkSearchResult item) {
        String airDate = item.getAirDate();
        // 未上映：无日期或日期为 0000-00-00
        if (airDate == null || airDate.isBlank() || airDate.startsWith("0000")) {
            return "UNRELEASED";
        }

        List<String> tags = item.getTags();
        if (tags != null) {
            for (String t : tags) {
                String lower = t.toLowerCase();
                if (lower.equals("ova") || lower.equals("oad")) return "OVA";
                if (lower.equals("movie") || lower.equals("剧场版") || lower.equals("映画")) return "MOVIE";
            }
        }

        Integer type = item.getSubjectType();
        Integer eps = item.getEpsCount();

        // 三次元 + 1-2集 → 电影
        if (type != null && type == 6 && eps != null && eps <= 2) return "MOVIE";

        // 多集 → TV
        if (eps != null && eps >= 6) return "TV";

        // 动画 + 单集 → 可能是 OVA/剧场版
        if (eps != null && eps == 1) return "OVA";

        // 其余归为 SPECIAL
        return "SPECIAL";
    }

    private String coalesceName(WorkSearchResult r) {
        if (r.getNameCn() != null && !r.getNameCn().isBlank()) return r.getNameCn();
        return r.getNameOrig();
    }

    private String normalizeDate(String d) {
        if (d == null || d.isBlank() || d.startsWith("0000")) return null;
        return d;
    }
}
