package com.nonu1l.media.repository;

import com.nonu1l.media.model.entity.ConversationSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConversationSessionRepository extends JpaRepository<ConversationSession, Long> {
}
