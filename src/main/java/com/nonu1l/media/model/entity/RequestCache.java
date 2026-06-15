package com.nonu1l.media.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 请求缓存实体：按 URL 与请求体缓存外部接口响应，避免重复请求。
 *
 * <p>规则：url 与 requestBody 组合唯一，GET 请求可将 requestBody 固定为 {@code ""}。</p>
 */
@Entity
@Table(name = "request_cache", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"url", "request_body"})
})
@Getter
@Setter
@NoArgsConstructor
public class RequestCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String url;

    @Column(name = "request_body", nullable = false)
    private String requestBody = "";

    @Column(name = "response_body", nullable = false)
    private String responseBody;

    @Column(name = "expire_time", nullable = false)
    private Instant expireTime;
}
