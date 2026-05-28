package com.nonu1l.media.model.dto;

import java.util.List;

public record AiChatResponse(
        Long messageId,
        String replyText,
        List<ConversationCardVO> cards
) {}
