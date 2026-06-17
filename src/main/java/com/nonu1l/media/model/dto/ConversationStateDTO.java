package com.nonu1l.media.model.dto;

import java.util.List;

/**
 * 会话状态快照 DTO。
 *
 * @param sessionId 会话 ID。
 * @param messages 消息列表。
 * @param cards 卡片列表。
 * @param activeRun 正在执行的对话轮次快照。
 */
public record ConversationStateDTO(
        Long sessionId,
        List<ConversationMessageDTO> messages,
        List<ConversationCardDTO> cards,
        ConversationRunStateDTO activeRun
) {}
