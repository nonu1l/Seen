package com.nonu1l.media.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * AI Web 搜索工具的结构化返回，失败时也给模型可判断的原因。
 *
 * @param ok 是否取得可用搜索结果。
 * @param query 原始查询词。
 * @param provider 实际使用的搜索源。
 * @param count 结果数量。
 * @param items 搜索结果列表。
 * @param error 失败或空结果原因。
 * @param hint 给 Agent 的下一步建议。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WebSearchResultDTO(
        boolean ok,
        String query,
        String provider,
        int count,
        List<WebSearchItemDTO> items,
        String error,
        String hint
) {
}
