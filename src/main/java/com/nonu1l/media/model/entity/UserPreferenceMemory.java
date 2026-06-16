package com.nonu1l.media.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 用户长期偏好画像快照。
 *
 * <p>当前系统按单用户本地部署处理，因此固定使用一条记录保存最新画像。</p>
 */
@Entity
@Table(name = "user_preference_memory")
@Getter
@Setter
@NoArgsConstructor
public class UserPreferenceMemory {

    @Id
    @Column(name = "id")
    private Long id;

    /** 画像版本号，每次成功重建递增。 */
    @Column(nullable = false)
    private Long version;

    /** 面向 Agent prompt 的短文本画像。 */
    @Column(columnDefinition = "TEXT")
    private String summary;

    /** 用户正向偏好 JSON。 */
    @Column(name = "likes_json", columnDefinition = "TEXT")
    private String likesJson;

    /** 用户负向偏好 JSON。 */
    @Column(name = "dislikes_json", columnDefinition = "TEXT")
    private String dislikesJson;

    /** 近期偏好变化 JSON。 */
    @Column(name = "recent_shift_json", columnDefinition = "TEXT")
    private String recentShiftJson;

    /** 推荐时应遵守的规则 JSON。 */
    @Column(name = "recommendation_rules_json", columnDefinition = "TEXT")
    private String recommendationRulesJson;

    /** 本轮参与总结数据的指纹，用于跳过重复重建。 */
    @Column(name = "source_hash", length = 80)
    private String sourceHash;

    /** 画像最后更新时间。 */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
