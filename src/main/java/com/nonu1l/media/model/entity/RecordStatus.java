package com.nonu1l.media.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "record_status")
@Getter
@Setter
@NoArgsConstructor
public class RecordStatus {

    @Id
    private String code;

    @Column(nullable = false)
    private String label;

    @Column(name = "sort_order")
    private Integer sortOrder;
}
