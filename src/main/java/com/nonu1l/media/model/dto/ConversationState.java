package com.nonu1l.media.model.dto;

import java.util.List;

/**
 * 会话状态快照 DTO。
 *
 * @param sessionId 会话 ID。
 * @param messages 消息列表。
 * @param cards 卡片列表。
 */
public record ConversationState(
        Long sessionId,
        List<ConversationMessageVO> messages,
        List<ConversationCardVO> cards
) {}
