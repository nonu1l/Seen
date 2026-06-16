package com.nonu1l.media.repository;

import com.nonu1l.media.model.entity.TokenUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Token 用量仓储：记录各会话节点对模型 Token 的消耗明细。
 *
 * <p>支持对话节点级消耗追踪与历史审计查询。</p>
 */
@Repository
public interface TokenUsageRepository extends JpaRepository<TokenUsage, Long> {

    /**
     * 汇总全部 Token 使用量。
     *
     * @return totalTokens 求和；无记录时返回 {@code null}
     */
    @Query("SELECT SUM(t.totalTokens) FROM TokenUsage t")
    Long sumTotalTokens();
}
