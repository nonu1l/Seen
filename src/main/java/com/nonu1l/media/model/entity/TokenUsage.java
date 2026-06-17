package com.nonu1l.media.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Token 使用明细实体：记录每次模型调用在会话与节点维度的消耗。
 */
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

    @Column(name = "profile_id")
    private Long profileId;

    @Column(name = "profile_name", length = 80)
    private String profileName;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    /** Provider 原生返回的 prompt cache 命中 token 数。 */
    @Column(name = "native_cached_tokens")
    private Long nativeCachedTokens;

    /** 关联的会话 ID，用于多轮追踪 */
    @Column(name = "session_id")
    private Long sessionId;

    /** 同一轮 AI 请求的追踪 ID。 */
    @Column(name = "request_id", length = 80)
    private String requestId;

    /** Agent 节点名称（autonomous-agent、tool、pipeline 等） */
    @Column(name = "node_name", length = 30)
    private String nodeName;

    /** 对话轮次，从 1 开始 */
    @Column(name = "turn")
    private Integer turn;

    @Column(name = "input_text", columnDefinition = "TEXT")
    private String inputText;

    @Column(name = "output_text", columnDefinition = "TEXT")
    private String outputText;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * 新建记录前补齐创建时间，确保审计时间完整。
     *
     * <p>关键副作用：若 {@code createdAt} 为空则自动写入当前时间。</p>
     */
    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
