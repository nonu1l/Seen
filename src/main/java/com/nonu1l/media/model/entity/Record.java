package com.nonu1l.media.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

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

    /** wish / doing / collect / on_hold / dropped */
    @Column(nullable = false)
    private String status;

    /** 0-10，步进 1；null = 未评分 */
    private Double rating;

    private String review;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
