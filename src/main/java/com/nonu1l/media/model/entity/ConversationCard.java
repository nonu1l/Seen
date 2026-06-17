package com.nonu1l.media.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 会话卡片实体：保存 AI 工具动作产生的作品展示快照和可撤销状态。
 */
@Entity
@Table(name = "conversation_card")
@Getter
@Setter
@NoArgsConstructor
public class ConversationCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    /** 同一轮 AI 请求的追踪 ID。 */
    @Column(name = "request_id", nullable = false, length = 80)
    private String requestId;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    /** 本卡片对应的动作类型：PRESENT / MARK / UPDATE / UNMARK / MANUAL_SAVE。 */
    @Column(name = "action_type", nullable = false, length = 30)
    private String actionType;

    @Column(name = "name_cn")
    private String nameCn;

    @Column(name = "cover_url")
    private String coverUrl;

    private String year;

    /** Bangumi 平台类型（TV / 剧场版 等） */
    @Column(length = 30)
    private String platform;

    /** 用户评分，10 分制，支持 0.5 分小数。 */
    private Double rating;

    /** Bangumi 条目评分 */
    private Double score;

    @Column(columnDefinition = "TEXT")
    private String review;

    @Column(length = 20)
    private String status;

    @Column(name = "previous_status", length = 20)
    private String previousStatus;

    @Column(name = "previous_rating")
    private Double previousRating;

    @Column(name = "previous_review", columnDefinition = "TEXT")
    private String previousReview;

    @Column(columnDefinition = "TEXT")
    private String tags;

    @Column(columnDefinition = "TEXT")
    private String plot;

    @Column(name = "card_state", nullable = false, length = 20)
    private String cardState;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * 新建卡片时补齐创建和更新时间。
     */
    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    /**
     * 更新卡片状态或内容时刷新更新时间。
     */
    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
