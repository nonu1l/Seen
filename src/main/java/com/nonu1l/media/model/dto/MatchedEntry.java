package com.nonu1l.media.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.Serializable;

/**
 * LLM Phase 2 输出 — 匹配到的单个条目。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MatchedEntry(
        /** Bangumi 条目 ID */
        Long subjectId,
        /** 中文名 */
        String nameCn,
        /** 推断的评分 1-10，null=未评分 */
        Integer rating,
        /** 评价文本 */
        String comment,
        /** 状态标记 */
        String status,
        /** 匹配置信度 0-1 */
        Double confidence,
        /** 播出/上映日期 */
        String airDate
) implements Serializable {
    public MatchedEntry(Long subjectId, String nameCn, Integer rating, String comment,
                        String status, Double confidence) {
        this(subjectId, nameCn, rating, comment, status, confidence, null);
    }
}
