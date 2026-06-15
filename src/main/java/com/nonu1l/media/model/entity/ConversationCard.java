package com.nonu1l.media.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 会话卡片实体：保存对话中展示的作品卡片快照信息。
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

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "name_cn")
    private String nameCn;

    @Column(name = "cover_url")
    private String coverUrl;

    private String year;

    /** Bangumi 平台类型（TV / 剧场版 等） */
    @Column(length = 30)
    private String platform;

    private Integer rating;

    /** Bangumi 条目评分 */
    private Double score;

    @Column(columnDefinition = "TEXT")
    private String review;

    @Column(length = 20)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String tags;

    @Column(columnDefinition = "TEXT")
    private String plot;

    @Column(name = "card_state", nullable = false, length = 20)
    private String cardState;
}
