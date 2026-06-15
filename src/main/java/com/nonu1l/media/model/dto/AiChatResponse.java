package com.nonu1l.media.model.dto;

import java.util.List;

/**
 * AI 对话返回体。
 * 包含消息主键、回复文本与推荐卡片列表。
 *
 * @param messageId 对话消息 ID。
 * @param replyText AI 生成的回复文本。
 * @param cards 推荐作品卡片集合。
 */
public record AiChatResponse(
        Long messageId,
        String replyText,
        List<ConversationCardVO> cards
) {}
