package com.nonu1l.media.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;

/**
 * Bangumi 精简条目，既用于 SearchPipeline 构建 LLM 输入，也作为 searchBangumi 工具返回。
 *
 * @param id Bangumi 条目 ID。
 * @param nameCn 中文名。
 * @param nameOrig 原始名/日文名。
 * @param category 条目类型或发布形态。
 * @param airDate 上映/开播日期。
 * @param epsCount 集数，可能为空。
 * @param score Bangumi 平均评分。
 * @param rank Bangumi 排名。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BangumiCompactSubjectDTO(
        Long id,
        String nameCn,
        String nameOrig,
        /** TV / OVA / MOVIE / UNRELEASED / SPECIAL */
        String category,
        /** 上映日期 yyyy-MM-dd，null=未知 */
        String airDate,
        /** 集数，null=未知 */
        Integer epsCount,
        /** Bangumi 平均评分 */
        Double score,
        /** Bangumi 排名 */
        Integer rank
) implements Serializable {

    /** 压缩为 LLM 友好的单行文本。 */
    public String toCompactLine() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(id).append("] ");
        sb.append(nameCn);
        sb.append(" | ").append(category);
        sb.append(" | ").append(airDate != null && !airDate.isBlank() ? airDate : "----");
        sb.append(" | ").append(epsCount != null ? epsCount + "集" : "?集");
        if (score != null && score > 0) {
            sb.append(" | 评分").append(String.format("%.1f", score));
        }
        if (rank != null && rank > 0) {
            sb.append(" | 排名").append(rank);
        }
        return sb.toString();
    }
}
