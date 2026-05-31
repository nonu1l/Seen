package com.nonu1l.media.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "token_usage")
@Getter
@Setter
@NoArgsConstructor
public class TokenUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_name", length = 50)
    private String modelName;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    /** 关联的会话 ID，用于多轮追踪 */
    @Column(name = "session_id")
    private Long sessionId;

    @Column(name = "input_text", columnDefinition = "TEXT")
    private String inputText;

    @Column(name = "output_text", columnDefinition = "TEXT")
    private String outputText;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
