package com.nonu1l.media.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Bangumi subject type — 2=动画, 6=真人 */
@Entity
@Table(name = "subject_type")
@Getter
@Setter
@NoArgsConstructor
public class SubjectType {

    @Id
    private Integer code;

    @Column(nullable = false)
    private String label;
}
