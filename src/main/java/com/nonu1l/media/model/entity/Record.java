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
