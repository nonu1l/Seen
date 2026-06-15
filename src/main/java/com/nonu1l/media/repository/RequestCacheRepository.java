package com.nonu1l.media.repository;

import com.nonu1l.media.model.entity.RequestCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * HTTP 请求缓存仓储：按 URL 与请求体命中最近未过期缓存，支持清理过期条目。
 *
 * <p>主要用于减少重复外部请求的频率，提高响应效率。</p>
 */
@Repository
public interface RequestCacheRepository extends JpaRepository<RequestCache, Long> {

    /**
     * 查询指定请求在当前时刻未过期的最新缓存。
     *
     * @param url 请求地址
     * @param requestBody 请求体
     * @param now 业务当前时间（通常使用 {@code Instant.now()}）
     * @return 未过期的最新缓存记录；无则返回 {@link Optional#empty()}
     */
    @Query("SELECT c FROM RequestCache c WHERE c.url = :url AND c.requestBody = :body AND c.expireTime > :now ORDER BY c.expireTime DESC LIMIT 1")
    Optional<RequestCache> findCache(@Param("url") String url, @Param("body") String requestBody, @Param("now") Instant now);

    /**
     * 查询指定请求的缓存记录，不区分是否过期。
     *
     * @param url 请求地址
     * @param requestBody 请求体
     * @return 已存在的缓存记录；无则返回 {@link Optional#empty()}
     */
    Optional<RequestCache> findFirstByUrlAndRequestBody(String url, String requestBody);

    /**
     * 删除指定时间点之前的缓存记录。
     *
     * <p>关键副作用：会批量删除过期缓存，释放数据库占用；无返回值。</p>
     *
     * @param now 时间阈值
     */
    void deleteByExpireTimeBefore(Instant now);
}
