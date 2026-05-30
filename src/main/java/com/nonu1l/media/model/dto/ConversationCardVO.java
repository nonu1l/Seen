package com.nonu1l.media.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

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
    /** 快速构造不含历史对比的 VO */
    public ConversationCardVO(Long id, Long messageId, Long subjectId,
                               String nameCn, String coverUrl, String year, String platform,
                               Integer rating, Double score, String review, String status, String cardState,
                               List<String> tags, String plot) {
        this(id, messageId, subjectId, nameCn, coverUrl, year, platform,
                rating, score, review, status, cardState, tags, plot,
                null, null, null);
    }
}
