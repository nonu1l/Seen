package com.nonu1l.media.repository;

import com.nonu1l.media.model.entity.ConversationCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConversationCardRepository extends JpaRepository<ConversationCard, Long> {

    List<ConversationCard> findAllBySessionIdAndCardStateOrderByIdAsc(Long sessionId, String cardState);

    List<ConversationCard> findAllBySessionIdAndCardStateInOrderByIdAsc(Long sessionId, List<String> cardStates);

    void deleteAllBySessionId(Long sessionId);
}
