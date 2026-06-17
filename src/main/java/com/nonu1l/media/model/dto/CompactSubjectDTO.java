package com.nonu1l.media.model.dto;

/**
 * 预处理后的精简条目，用于构建 LLM 输入文本。
 */
public record CompactSubjectDTO(
        Long id,
        String nameCn,
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
) {

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
