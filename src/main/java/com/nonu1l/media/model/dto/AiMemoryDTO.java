package com.nonu1l.media.model.dto;

import java.time.Instant;

/**
 * AI 长期记忆后台接口响应。
 *
 * @param exists 是否已有画像
 * @param version 画像版本号
 * @param summary 短文本画像
 * @param likesJson 正向偏好 JSON
 * @param dislikesJson 负向偏好 JSON
 * @param recentShiftJson 近期偏好变化 JSON
 * @param recommendationRulesJson 推荐规则 JSON
 * @param sourceHash 画像来源数据指纹
 * @param updatedAt 画像最后更新时间
 */
public record AiMemoryDTO(
        boolean exists,
        Long version,
        String summary,
        String likesJson,
        String dislikesJson,
        String recentShiftJson,
        String recommendationRulesJson,
        String sourceHash,
        Instant updatedAt
) {
}
