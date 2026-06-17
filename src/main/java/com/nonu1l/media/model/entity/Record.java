package com.nonu1l.media.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 作品记录实体：保存用户对单个作品的状态、评分与评价快照。
 */
@Entity
@Table(name = "record")
@Getter
@Setter
@NoArgsConstructor
public class Record {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "work_id", nullable = false)
    private Long workId;

    /** AI 请求 ID；手动操作可为空。 */
    @Column(name = "request_id", length = 80)
    private String requestId;

    /** 触发该记录的会话卡片 ID；非 AI 卡片操作可为空。 */
    @Column(name = "card_id")
    private Long cardId;

    /** 创建来源：USER / AI / SYSTEM。 */
    @Column(name = "created_by", length = 20)
    private String createdBy;

    @Column(nullable = false)
    private String status;

    private Double rating;

    private String review;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * 新建记录前补齐创建和更新时间。
     *
     * <p>关键副作用：在首次落库时自动写入 {@code createdAt} 与 {@code updatedAt}。</p>
     */
    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (this.createdAt == null) this.createdAt = now;
        if (this.updatedAt == null) this.updatedAt = now;
    }

    /**
     * 更新记录前刷新最后更新时间。
     *
     * <p>关键副作用：每次更新时强制覆盖 {@code updatedAt} 为当前时间。</p>
     */
    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
