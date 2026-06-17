package com.nonu1l.media.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 会话消息实体：持久化单条用户/模型消息内容与时间戳。
 */
@Entity
@Table(name = "conversation_message")
@Getter
@Setter
@NoArgsConstructor
public class ConversationMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    /** 同一轮 AI 请求的追踪 ID；用户消息和助手消息共享。 */
    @Column(name = "request_id", length = 80)
    private String requestId;

    @Column(nullable = false, length = 10)
    private String role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
