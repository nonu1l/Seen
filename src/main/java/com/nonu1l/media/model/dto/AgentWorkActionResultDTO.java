package com.nonu1l.media.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * AI 作品写操作工具的结构化返回，避免业务失败直接中断整轮 Agent。
 *
 * @param ok 操作是否成功产生预期卡片或记录。
 * @param action 操作类型，如 present / mark / unmark。
 * @param subjectId 单个操作的作品 ID。
 * @param card 单个操作产生的卡片。
 * @param cards 批量展示操作产生的卡片列表。
 * @param error 失败原因。
 * @param hint 给 Agent 的下一步建议。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentWorkActionResultDTO(
        boolean ok,
        String action,
        Long subjectId,
        ConversationCardDTO card,
        List<ConversationCardDTO> cards,
        String error,
        String hint
) {
}
