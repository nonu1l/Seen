package com.nonu1l.media.repository;

import com.nonu1l.media.model.entity.ConversationMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {

    List<ConversationMessage> findAllBySessionIdOrderByIdAsc(Long sessionId);

    long countBySessionIdAndRole(Long sessionId, String role);

    void deleteAllBySessionId(Long sessionId);
}
