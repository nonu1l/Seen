package com.nonu1l.media.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 应用运行时设置，保存用户在设置页修改的可变配置。
 */
@Entity
@Table(name = "app_setting", uniqueConstraints = {
        @UniqueConstraint(name = "uk_app_setting_key", columnNames = "setting_key")
})
@Getter
@Setter
@NoArgsConstructor
public class AppSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "setting_key", nullable = false, length = 120)
    private String settingKey;

    @Column(name = "setting_value", columnDefinition = "TEXT")
    private String settingValue;

    @Column(name = "value_type", nullable = false, length = 30)
    private String valueType;

    @Column(name = "sensitive", nullable = false)
    private boolean sensitive;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touchUpdatedAt() {
        updatedAt = Instant.now();
    }
}
