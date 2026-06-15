package com.nonu1l.media.model.dto;

import java.time.Instant;

/**
 * 会话消息展示 DTO。
 *
 * @param id 消息 ID。
 * @param role 角色（user/assistant/system）。
 * @param content 消息正文。
 * @param createdAt 创建时间戳。
 */
public record ConversationMessageVO(
        Long id,
        String role,
        String content,
        Instant createdAt
) {}
