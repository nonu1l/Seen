package com.nonu1l.media.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 对话消息中嵌入的作品卡片 VO。
 *
 * @param id 卡片自身 ID。
 * @param messageId 归属的会话消息 ID。
 * @param subjectId 作品条目 ID。
 * @param nameCn 中文名称。
 * @param coverUrl 封面图链接。
 * @param year 年份信息。
 * @param platform 来源平台。
 * @param rating 用户评分。
 * @param score 平均评分。
 * @param review 用户评价内容。
 * @param status 观看状态。
 * @param cardState 卡片状态。
 * @param tags 标签列表。
 * @param plot 简介文本。
 * @param previousRating 历史记录中的评分。
 * @param previousReview 历史记录中的评价。
 * @param previousStatus 历史记录中的状态。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConversationCardVO(
        Long id,
        Long messageId,
        Long subjectId,
        String nameCn,
        String coverUrl,
        String year,
        String platform,
        Integer rating,
        Double score,
        String review,
        String status,
        String cardState,
        List<String> tags,
        String plot,
        /** 以下为 AI 新增记录时的历史对比信息 */
        Integer previousRating,
        String previousReview,
        String previousStatus
) {
    /**
     * 创建不含历史对比信息的卡片 VO。
     *
     * @param id 卡片自身 ID。
     * @param messageId 归属的会话消息 ID。
     * @param subjectId 作品条目 ID。
     * @param nameCn 中文名称。
     * @param coverUrl 封面图链接。
     * @param year 年份信息。
     * @param platform 来源平台。
     * @param rating 用户评分。
     * @param score 平均评分。
     * @param review 用户评价内容。
     * @param status 观看状态。
     * @param cardState 卡片状态。
     * @param tags 标签列表。
     * @param plot 简介文本。
     */
    public ConversationCardVO(Long id, Long messageId, Long subjectId,
                               String nameCn, String coverUrl, String year, String platform,
                               Integer rating, Double score, String review, String status, String cardState,
                               List<String> tags, String plot) {
        this(id, messageId, subjectId, nameCn, coverUrl, year, platform,
                rating, score, review, status, cardState, tags, plot,
                null, null, null);
    }
}
