package com.nonu1l.media.model.dto;

/**
 * Token 使用详情 DTO。
 *
 * @param id 流水主键。
 * @param profileName AI Provider Profile 名称。
 * @param modelName 模型名称。
 * @param totalTokens 总 token 数。
 * @param inputText 输入片段。
 * @param outputText 输出片段。
 */
public record TokenUsageDetailDTO(
    Long id,
    String profileName,
    String modelName,
    Long totalTokens,
    String inputText,
    String outputText
) {}
