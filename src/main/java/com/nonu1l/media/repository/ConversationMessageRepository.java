package com.nonu1l.media.repository;

import com.nonu1l.media.model.entity.ConversationMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 对话消息仓储：提供按会话维度的消息查询、计数与清理能力。
 *
 * <p>用于支持会话消息列表展示和角色维度统计，不承担跨领域聚合计算。</p>
 */
@Repository
public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {

    /**
     * 查询指定会话的全部消息，按 id 升序返回。
     *
     * @param sessionId 会话 ID
     * @return 该会话内的消息列表（按 id 升序）
     */
    List<ConversationMessage> findAllBySessionIdOrderByIdAsc(Long sessionId);

    /**
     * 统计指定会话下特定角色的消息条数。
     *
     * @param sessionId 会话 ID
     * @param role 消息角色（如 user/assistant）
     * @return 对应角色消息数量
     */
    long countBySessionIdAndRole(Long sessionId, String role);

    /**
     * 删除指定会话下全部消息。
     *
     * <p>关键副作用：会清空该会话所有消息记录，后续无法通过仓储恢复。</p>
     *
     * @param sessionId 会话 ID
     */
    void deleteAllBySessionId(Long sessionId);
}
