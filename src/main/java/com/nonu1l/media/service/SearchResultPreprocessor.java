package com.nonu1l.media.service;

import com.nonu1l.media.model.dto.CompactSubjectDTO;
import com.nonu1l.media.model.dto.WorkSearchResultDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Bangumi 搜索结果预处理 — 纯 Java，零 token 消耗。
 * 负责：条目分类和紧凑 DTO 映射。
 */
@Component
public class SearchResultPreprocessor {

    /**
     * 对搜索结果进行过滤与分类。
     *
     * @param rawResults Bangumi 原始搜索结果
     * @return 预处理后的精简条目列表
     */
    public List<CompactSubjectDTO> preprocess(List<WorkSearchResultDTO> rawResults) {
        if (rawResults == null || rawResults.isEmpty()) return List.of();

        List<CompactSubjectDTO> subjects = new ArrayList<>();
        for (WorkSearchResultDTO r : rawResults) {
            if (r.getId() == null) continue;
            String category = classify(r);
            subjects.add(new CompactSubjectDTO(
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
//                .comparing(CompactSubjectDTO::rank, Comparator.nullsLast(
//                        Comparator.naturalOrder()))
//                .thenComparing(CompactSubjectDTO::airDate, Comparator.nullsLast(
//                        Comparator.naturalOrder()))
//                .thenComparing(CompactSubjectDTO::id));
//
        return subjects;
    }

    /**
     * 条目分类：TV / OVA / MOVIE / UNRELEASED / SPECIAL。
     *
     * @param item 条目
     * @return 分类标识字符串
     */
    public String classify(WorkSearchResultDTO item) {
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

    private String coalesceName(WorkSearchResultDTO r) {
        if (r.getNameCn() != null && !r.getNameCn().isBlank()) return r.getNameCn();
        return r.getNameOrig();
    }

    private String normalizeDate(String d) {
        if (d == null || d.isBlank() || d.startsWith("0000")) return null;
        return d;
    }
}
