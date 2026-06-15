package com.nonu1l.media.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 条目类型实体：保存 Bangumi 条目类型码与可展示文案。
 */
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
