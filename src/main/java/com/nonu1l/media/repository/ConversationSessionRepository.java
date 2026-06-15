package com.nonu1l.media.repository;

import com.nonu1l.media.model.entity.ConversationSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 会话主表仓储：管理会话生命周期基础数据。
 *
 * <p>该仓储主要用于会话实体的标准增删改查与生命周期查询。</p>
 */
@Repository
public interface ConversationSessionRepository extends JpaRepository<ConversationSession, Long> {
}
