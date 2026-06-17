package com.nonu1l.media.repository;

import com.nonu1l.media.model.entity.ConversationCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 会话卡片仓储：管理某个对话会话内的卡片记录及其状态查询。
 *
 * <p>仓储仅负责卡片的检索与清理，不承载业务计算逻辑。</p>
 */
@Repository
public interface ConversationCardRepository extends JpaRepository<ConversationCard, Long> {

    /**
     * 按会话与卡片状态查询卡片列表，按 id 升序返回。
     *
     * @param sessionId 会话 ID
     * @param cardState 卡片状态
     * @return 指定会话内该状态的卡片列表（按创建序/主键序升序）
     */
    List<ConversationCard> findAllBySessionIdAndCardStateOrderByIdAsc(Long sessionId, String cardState);

    /**
     * 按会话与多种状态查询卡片列表，按 id 升序返回。
     *
     * @param sessionId 会话 ID
     * @param cardStates 需要匹配的卡片状态集合
     * @return 指定会话内状态在集合内的卡片列表
     */
    List<ConversationCard> findAllBySessionIdAndCardStateInOrderByIdAsc(Long sessionId, List<String> cardStates);

    /**
     * 查询某轮 AI 请求创建的全部卡片。
     *
     * @param sessionId 会话 ID
     * @param requestId AI 请求 ID
     * @return 本轮请求产生的卡片列表
     */
    List<ConversationCard> findAllBySessionIdAndRequestIdOrderByIdAsc(Long sessionId, String requestId);

    /**
     * 查询某轮请求中指定作品的最后一张卡片。
     *
     * @param sessionId 会话 ID
     * @param requestId AI 请求 ID
     * @param subjectId 作品 ID
     * @return 匹配卡片列表，按 ID 倒序
     */
    List<ConversationCard> findAllBySessionIdAndRequestIdAndSubjectIdOrderByIdDesc(
            Long sessionId, String requestId, Long subjectId);

    /**
     * 删除指定会话下全部卡片。
     *
     * <p>关键副作用：会直接从数据库移除所有匹配会话的卡片记录，操作不可恢复。</p>
     *
     * @param sessionId 会话 ID
     */
    void deleteAllBySessionId(Long sessionId);
}
