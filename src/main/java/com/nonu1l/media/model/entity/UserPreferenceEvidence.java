package com.nonu1l.media.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 用户长期偏好画像的可解释证据。
 *
 * <p>证据由本地作品与记录派生，可在重建画像时整体删除后重新生成。</p>
 */
@Entity
@Table(name = "user_preference_evidence")
@Getter
@Setter
@NoArgsConstructor
public class UserPreferenceEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 证据关联的作品 ID。 */
    @Column(name = "work_id", nullable = false)
    private Long workId;

    /** 证据关联的记录 ID。 */
    @Column(name = "record_id")
    private Long recordId;

    /** 证据类型，如 high_rating、low_rating、review、status、recent。 */
    @Column(name = "evidence_type", nullable = false, length = 40)
    private String evidenceType;

    /** 证据权重，用于给 LLM 排序和提示重点。 */
    private Double weight;

    /** 提炼后的证据文本。 */
    @Column(columnDefinition = "TEXT")
    private String text;

    /** 证据生成时间。 */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
