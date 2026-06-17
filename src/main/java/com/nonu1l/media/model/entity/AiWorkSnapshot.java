package com.nonu1l.media.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * AI 操作前的作品完整快照，用于按 request 恢复 work 与 record 历史。
 */
@Entity
@Table(name = "ai_work_snapshot", uniqueConstraints = {
        @UniqueConstraint(name = "uk_ai_work_snapshot_request_subject",
                columnNames = {"session_id", "request_id", "subject_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class AiWorkSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "request_id", nullable = false, length = 80)
    private String requestId;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "work_snapshot", columnDefinition = "TEXT")
    private String workSnapshot;

    @Column(name = "records_snapshot", columnDefinition = "TEXT")
    private String recordsSnapshot;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "restored_at")
    private Instant restoredAt;

    /**
     * 新建快照时写入创建时间和默认状态。
     */
    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (status == null || status.isBlank()) status = "ACTIVE";
    }
}
