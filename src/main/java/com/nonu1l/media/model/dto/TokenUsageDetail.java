package com.nonu1l.media.model.dto;

public record TokenUsageDetail(
    Long id,
    String modelName,
    Long totalTokens,
    String inputText,
    String outputText
) {}
