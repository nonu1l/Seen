package com.nonu1l.media.model.dto;

import java.time.Instant;

public record ConversationMessageVO(
        Long id,
        String role,
        String content,
        Instant createdAt
) {}
