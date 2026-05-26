package com.nonu1l.media.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 作品实体。
 * 表示一个影视/动画作品，数据来自 Bangumi API。
 * 每个 Work 可以有多条 Record（标记记录）。
 */
@Entity
@Table(name = "work")
@Getter
@Setter
@NoArgsConstructor
public class Work {

    /** Bangumi 条目 ID */
    @Id
    @Column(name = "id")
    private Long id;

    /** 原始标题 */
    private String name;

    /** 中文标题 */
    private String nameCn;

    /** 作品平台类型（如 TV、电影、剧场版） */
    private String platform;

    /** 创建时间 */
    @Column(updatable = false)
    private Instant createdAt;

    /** 更新时间 */
    private Instant updatedAt;
}
