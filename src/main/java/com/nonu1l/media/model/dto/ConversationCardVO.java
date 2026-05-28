package com.nonu1l.media.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

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
        String review,
        String status,
        String cardState,
        /** 以下为 AI 新增记录时的历史对比信息 */
        Integer previousRating,
        String previousReview,
        String previousStatus
) {
    /** 快速构造不含历史对比的 VO */
    public ConversationCardVO(Long id, Long messageId, Long subjectId,
                               String nameCn, String coverUrl, String year, String platform,
                               Integer rating, String review, String status, String cardState) {
        this(id, messageId, subjectId, nameCn, coverUrl, year, platform,
                rating, review, status, cardState, null, null, null);
    }
}
