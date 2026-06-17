package com.nonu1l.media.service;

import com.nonu1l.media.model.dto.ConversationRunState;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 保存当前进程内正在执行的 AI 对话轮次，供页面重开后恢复状态与回复片段。
 */
@Service
public class ConversationRunStore {

    private static final int MAX_STATUSES = 6;

    private final ConcurrentMap<Long, ActiveRun> runs = new ConcurrentHashMap<>();

    /**
     * 开始记录某个会话的新一轮 AI 处理。
     *
     * @param sessionId 会话 ID
     * @param userMessageId 已落库的用户消息 ID
     * @param startedAt 本轮开始时间
     */
    public void start(Long sessionId, Long userMessageId, Instant startedAt) {
        if (sessionId == null) {
            return;
        }
        runs.put(sessionId, new ActiveRun(userMessageId, startedAt != null ? startedAt : Instant.now()));
    }

    /**
     * 记录一条流程状态。
     *
     * @param sessionId 会话 ID
     * @param status 状态文案
     */
    public void status(Long sessionId, String status) {
        ActiveRun run = run(sessionId);
        if (run != null) {
            run.status(status);
        }
    }

    /**
     * 追加助手回复增量。
     *
     * @param sessionId 会话 ID
     * @param delta 回复增量文本
     */
    public void delta(Long sessionId, String delta) {
        ActiveRun run = run(sessionId);
        if (run != null) {
            run.delta(delta);
        }
    }

    /**
     * 记录助手消息已落库的最终状态。
     *
     * @param sessionId 会话 ID
     * @param assistantMessageId 助手消息 ID
     * @param content 完整助手回复
     * @param updatedAt 更新时间
     */
    public void assistantSaved(Long sessionId, Long assistantMessageId, String content, Instant updatedAt) {
        ActiveRun run = run(sessionId);
        if (run != null) {
            run.assistantSaved(assistantMessageId, content, updatedAt);
        }
    }

    /**
     * 记录本轮错误文案。
     *
     * @param sessionId 会话 ID
     * @param message 错误文案
     */
    public void error(Long sessionId, String message) {
        ActiveRun run = run(sessionId);
        if (run != null) {
            run.error(message);
        }
    }

    /**
     * 获取某个会话当前活动轮次的只读快照。
     *
     * @param sessionId 会话 ID
     * @return 活动轮次快照；无活动轮次时返回 inactive
     */
    public ConversationRunState snapshot(Long sessionId) {
        ActiveRun run = run(sessionId);
        return run != null ? run.snapshot() : ConversationRunState.inactive();
    }

    /**
     * 完成并移除某个会话的活动轮次。
     *
     * @param sessionId 会话 ID
     */
    public void complete(Long sessionId) {
        if (sessionId != null) {
            runs.remove(sessionId);
        }
    }

    /**
     * 清理某个会话的活动轮次，通常用于会话重置。
     *
     * @param sessionId 会话 ID
     */
    public void clear(Long sessionId) {
        complete(sessionId);
    }

    private ActiveRun run(Long sessionId) {
        return sessionId == null ? null : runs.get(sessionId);
    }

    private static final class ActiveRun {
        private final Long userMessageId;
        private final Instant startedAt;
        private final StringBuilder assistantContent = new StringBuilder();
        private final List<String> statuses = new ArrayList<>();
        private Long assistantMessageId;
        private Instant updatedAt;
        private String error;

        private ActiveRun(Long userMessageId, Instant startedAt) {
            this.userMessageId = userMessageId;
            this.startedAt = startedAt;
            this.updatedAt = startedAt;
        }

        private synchronized void status(String status) {
            if (status == null || status.isBlank()) {
                return;
            }
            if (statuses.isEmpty() || !statuses.getLast().equals(status)) {
                statuses.add(status);
                while (statuses.size() > MAX_STATUSES) {
                    statuses.removeFirst();
                }
            }
            updatedAt = Instant.now();
        }

        private synchronized void delta(String delta) {
            if (delta == null || delta.isEmpty()) {
                return;
            }
            assistantContent.append(delta);
            updatedAt = Instant.now();
        }

        private synchronized void assistantSaved(Long assistantMessageId, String content, Instant updatedAt) {
            this.assistantMessageId = assistantMessageId;
            if (content != null) {
                assistantContent.setLength(0);
                assistantContent.append(content);
            }
            this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
        }

        private synchronized void error(String message) {
            if (message == null || message.isBlank()) {
                return;
            }
            this.error = message;
            this.updatedAt = Instant.now();
        }

        private synchronized ConversationRunState snapshot() {
            return new ConversationRunState(
                    true,
                    userMessageId,
                    assistantMessageId,
                    assistantContent.toString(),
                    List.copyOf(statuses),
                    startedAt,
                    updatedAt,
                    error
            );
        }
    }
}
