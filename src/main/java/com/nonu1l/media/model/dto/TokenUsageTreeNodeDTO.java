package com.nonu1l.media.model.dto;

import java.util.List;

/**
 * Token 使用树节点 DTO。
 * 用于展示聚合后的 token 消耗和调用次数层级结构。
 *
 * @param key 节点唯一键。
 * @param label 节点展示名。
 * @param totalTokens 当前节点总 token。
 * @param promptTokens prompt token 数。
 * @param completionTokens completion token 数。
 * @param callCount 调用次数。
 * @param subtitle 辅助说明文本。
 * @param children 子节点列表。
 */
public record TokenUsageTreeNodeDTO(
    String key,
    String label,
    long totalTokens,
    long promptTokens,
    long completionTokens,
    int callCount,
    String subtitle,
    List<TokenUsageTreeNodeDTO> children
) {
    /**
     * 创建无子节点、无说明文案的节点。
     *
     * @param key 节点唯一键。
     * @param label 节点展示名。
     * @param totalTokens 当前节点总 token。
     * @param promptTokens prompt token 数。
     * @param completionTokens completion token 数。
     * @param callCount 调用次数。
     */
    public TokenUsageTreeNodeDTO(String key, String label, long totalTokens, long promptTokens,
                              long completionTokens, int callCount) {
        this(key, label, totalTokens, promptTokens, completionTokens, callCount, null, List.of());
    }

    /**
     * 创建无子节点、带说明文案的节点。
     *
     * @param key 节点唯一键。
     * @param label 节点展示名。
     * @param totalTokens 当前节点总 token。
     * @param promptTokens prompt token 数。
     * @param completionTokens completion token 数。
     * @param callCount 调用次数。
     * @param subtitle 辅助说明文本。
     */
    public TokenUsageTreeNodeDTO(String key, String label, long totalTokens, long promptTokens,
                              long completionTokens, int callCount, String subtitle) {
        this(key, label, totalTokens, promptTokens, completionTokens, callCount, subtitle, List.of());
    }

    /**
     * 创建带子节点的节点。
     *
     * @param key 节点唯一键。
     * @param label 节点展示名。
     * @param totalTokens 当前节点总 token。
     * @param promptTokens prompt token 数。
     * @param completionTokens completion token 数。
     * @param callCount 调用次数。
     * @param children 子节点列表。
     */
    public TokenUsageTreeNodeDTO(String key, String label, long totalTokens, long promptTokens,
                              long completionTokens, int callCount, List<TokenUsageTreeNodeDTO> children) {
        this(key, label, totalTokens, promptTokens, completionTokens, callCount, null, children);
    }
}
