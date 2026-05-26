package com.nonu1l.media.repository;

import com.nonu1l.media.model.entity.RequestCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface RequestCacheRepository extends JpaRepository<RequestCache, Long> {

    /** 查询未过期的缓存 */
    @Query("SELECT c FROM RequestCache c WHERE c.url = :url AND c.requestBody = :body AND c.expireTime > :now ORDER BY c.expireTime DESC LIMIT 1")
    Optional<RequestCache> findCache(@Param("url") String url, @Param("body") String requestBody, @Param("now") Instant now);

    /** 删除所有已过期的缓存 */
    void deleteByExpireTimeBefore(Instant now);
}
