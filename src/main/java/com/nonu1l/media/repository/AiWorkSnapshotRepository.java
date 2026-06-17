package com.nonu1l.media.repository;

import com.nonu1l.media.model.entity.AiWorkSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * AI 作品快照仓储：按会话请求和作品定位可恢复的操作前状态。
 */
@Repository
public interface AiWorkSnapshotRepository extends JpaRepository<AiWorkSnapshot, Long> {

    Optional<AiWorkSnapshot> findBySessionIdAndRequestIdAndSubjectId(Long sessionId, String requestId, Long subjectId);

    void deleteAllBySessionId(Long sessionId);
}
