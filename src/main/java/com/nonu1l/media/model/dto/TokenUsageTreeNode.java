package com.nonu1l.media.model.dto;

import java.util.List;

public record TokenUsageTreeNode(
    String key,
    String label,
    long totalTokens,
    long promptTokens,
    long completionTokens,
    int callCount,
    String subtitle,
    List<TokenUsageTreeNode> children
) {
    public TokenUsageTreeNode(String key, String label, long totalTokens, long promptTokens,
                              long completionTokens, int callCount) {
        this(key, label, totalTokens, promptTokens, completionTokens, callCount, null, List.of());
    }

    public TokenUsageTreeNode(String key, String label, long totalTokens, long promptTokens,
                              long completionTokens, int callCount, String subtitle) {
        this(key, label, totalTokens, promptTokens, completionTokens, callCount, subtitle, List.of());
    }

    public TokenUsageTreeNode(String key, String label, long totalTokens, long promptTokens,
                              long completionTokens, int callCount, List<TokenUsageTreeNode> children) {
        this(key, label, totalTokens, promptTokens, completionTokens, callCount, null, children);
    }
}
