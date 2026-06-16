package com.nonu1l.media.repository;

import com.nonu1l.media.model.entity.UserPreferenceMemory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 用户长期偏好画像仓储。
 */
@Repository
public interface UserPreferenceMemoryRepository extends JpaRepository<UserPreferenceMemory, Long> {
}
