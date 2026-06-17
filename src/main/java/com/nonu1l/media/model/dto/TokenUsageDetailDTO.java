package com.nonu1l.media.model.dto;

/**
 * Token 使用详情 DTO。
 *
 * @param id 流水主键。
 * @param profileName AI Provider Profile 名称。
 * @param modelName 模型名称。
 * @param requestId AI 请求 ID。
 * @param totalTokens 总 token 数。
 * @param promptTokens prompt token 数。
 * @param nativeCachedTokens provider 原生返回的 prompt cache 命中 token 数。
 * @param inputText 输入片段。
 * @param outputText 输出片段。
 */
public record TokenUsageDetailDTO(
    Long id,
    String profileName,
    String modelName,
    String requestId,
    Long totalTokens,
    Long promptTokens,
    Long nativeCachedTokens,
    String inputText,
    String outputText
) {}
