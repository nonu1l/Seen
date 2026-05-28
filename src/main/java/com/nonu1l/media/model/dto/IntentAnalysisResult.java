package com.nonu1l.media.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record IntentAnalysisResult(
        String replyText,
        List<MatchedEntry> entries,
        List<Long> unmarkIds
) {}
