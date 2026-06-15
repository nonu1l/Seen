package com.nonu1l.media.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 作品实体：保存 Bangumi 作品元数据及其展示快照。
 *
 * <p>该表是作品搜索与会话卡片引用的主数据来源。</p>
 */
@Entity
@Table(name = "work")
@Getter
@Setter
@NoArgsConstructor
public class Work {

    @Id
    @Column(name = "id")
    private Long id;

    /** 原始标题 */
    private String name;

    /** 中文标题 */
    private String nameCn;

    /** Bangumi 平台类型（TV / 剧场版 / OVA 等） */
    private String platform;

    /** 封面图 URL */
    private String coverUrl;

    /** 年份 */
    private String year;

    /** 简介 */
    @Column(columnDefinition = "TEXT")
    private String plot;

    /** Bangumi 评分 */
    private Double score;

    /** 标签（JSON 数组字符串） */
    @Column(columnDefinition = "TEXT")
    private String tagsCache;

    @Column(updatable = false)
    private Instant createdAt;

    private Instant updatedAt;
}
