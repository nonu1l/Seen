package com.nonu1l.media.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * 对话消息中嵌入的作品卡片 DTO。
 *
 * @param id 卡片自身 ID。
 * @param messageId 归属的会话消息 ID。
 * @param requestId 归属的 AI 请求 ID。
 * @param subjectId 作品条目 ID。
 * @param actionType 卡片对应的动作类型。
 * @param nameCn 中文名称。
 * @param coverUrl 封面图链接。
 * @param year 年份信息。
 * @param platform 来源平台。
 * @param rating 用户评分，10 分制，支持 0.5 分小数。
 * @param score 平均评分。
 * @param review 用户评价内容。
 * @param status 观看状态。
 * @param cardState 卡片状态。
 * @param tags 标签列表。
 * @param plot 简介文本。
 * @param previousRating 历史记录中的评分，10 分制，支持 0.5 分小数。
 * @param previousReview 历史记录中的评价。
 * @param previousStatus 历史记录中的状态。
 * @param createdAt 创建时间。
 * @param updatedAt 更新时间。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConversationCardDTO(
        Long id,
        Long messageId,
        String requestId,
        Long subjectId,
        String actionType,
        String nameCn,
        String coverUrl,
        String year,
        String platform,
        Double rating,
        Double score,
        String review,
        String status,
        String cardState,
        List<String> tags,
        String plot,
        /** 以下为 AI 新增记录时的历史对比信息 */
        Double previousRating,
        String previousReview,
        String previousStatus,
        Instant createdAt,
        Instant updatedAt
) {
    /**
     * 创建不含历史对比信息的卡片 DTO。
     *
     * @param id 卡片自身 ID。
     * @param messageId 归属的会话消息 ID。
     * @param requestId 归属的 AI 请求 ID。
     * @param subjectId 作品条目 ID。
     * @param actionType 卡片对应的动作类型。
     * @param nameCn 中文名称。
     * @param coverUrl 封面图链接。
     * @param year 年份信息。
     * @param platform 来源平台。
     * @param rating 用户评分，10 分制，支持 0.5 分小数。
     * @param score 平均评分。
     * @param review 用户评价内容。
     * @param status 观看状态。
     * @param cardState 卡片状态。
     * @param tags 标签列表。
     * @param plot 简介文本。
     */
    public ConversationCardDTO(Long id, Long messageId, Long subjectId,
                               String nameCn, String coverUrl, String year, String platform,
                               Double rating, Double score, String review, String status, String cardState,
                               List<String> tags, String plot) {
        this(id, messageId, null, subjectId, null, nameCn, coverUrl, year, platform,
                rating, score, review, status, cardState, tags, plot,
                null, null, null, null, null);
    }
}
