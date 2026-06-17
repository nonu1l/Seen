package com.nonu1l.media.model.dto;

import java.util.List;

/**
 * AI 对话 DTO。
 * 包含消息主键、回复文本与推荐卡片列表。
 *
 * @param messageId 对话消息 ID。
 * @param replyText AI 生成的回复文本。
 * @param cards 推荐作品卡片集合。
 */
public record AiChatDTO(
        Long messageId,
        String replyText,
        List<ConversationCardDTO> cards
) {}
