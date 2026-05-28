package com.nonu1l.media.model.dto;

import java.util.List;

public record ConversationState(
        Long sessionId,
        List<ConversationMessageVO> messages,
        List<ConversationCardVO> cards
) {}
