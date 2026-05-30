package com.nonu1l.media.repository;

import com.nonu1l.media.model.entity.TokenUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TokenUsageRepository extends JpaRepository<TokenUsage, Long> {
}
