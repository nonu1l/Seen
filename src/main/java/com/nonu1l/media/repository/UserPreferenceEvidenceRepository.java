package com.nonu1l.media.repository;

import com.nonu1l.media.model.entity.UserPreferenceEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 用户长期偏好证据仓储。
 */
@Repository
public interface UserPreferenceEvidenceRepository extends JpaRepository<UserPreferenceEvidence, Long> {
}
