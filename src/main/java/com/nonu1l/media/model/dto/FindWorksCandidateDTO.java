package com.nonu1l.media.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.Serializable;

/**
 * LLM Phase 2 输出 — 匹配到的单个条目。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FindWorksCandidateDTO(
        /** Bangumi 条目 ID */
        Long subjectId,
        /** 中文名 */
        String nameCn,
        /** 推断的评分 1-10，支持 0.5 分小数，null=未评分 */
        Double rating,
        /** 评价文本 */
        String comment,
        /** 状态标记 */
        String status,
        /** 匹配置信度 0-1 */
        Double confidence,
        /** 播出/上映日期 */
        String airDate
) implements Serializable {
    /**
     * 使用默认空播出日期构造匹配条目。
     *
     * @param subjectId Bangumi 条目 ID。
     * @param nameCn 中文名称。
     * @param rating 用户评分（1-10，支持 0.5 分小数）。
     * @param comment 用户评价文本。
     * @param status 标记状态。
     * @param confidence 匹配置信度（0-1）。
     */
    public FindWorksCandidateDTO(Long subjectId, String nameCn, Double rating, String comment,
                        String status, Double confidence) {
        this(subjectId, nameCn, rating, comment, status, confidence, null);
    }
}
