package com.nonu1l.media.service;

import com.nonu1l.media.model.dto.ConversationRunStateDTO;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 保存当前进程内正在执行的单例 AI 对话轮次，供页面重开后恢复状态并支持停止。
 */
@Service
public class ConversationRunStore {

    private static final int MAX_STATUSES = 6;

    private final AtomicReference<ActiveRun> activeRun = new AtomicReference<>();
    private final Set<Long> stoppedUserMessages = ConcurrentHashMap.newKeySet();
    private final Set<Long> stoppedAssistantSavedMessages = ConcurrentHashMap.newKeySet();

    /**
     * 尝试占用全局 AI 运行槽位。
     *
     * @param sessionId 会话 ID
     * @param requestId 本轮请求 ID
     * @param startedAt 本轮开始时间
     * @return 成功占用返回 true；已有未停止任务时返回 false
     */
    public boolean reserve(Long sessionId, String requestId, Instant startedAt) {
        if (sessionId == null || requestId == null || requestId.isBlank()) {
            return false;
        }
        ActiveRun next = new ActiveRun(sessionId, requestId, startedAt != null ? startedAt : Instant.now());
        while (true) {
            ActiveRun current = activeRun.get();
            if (current != null && !current.isStopped()) {
                return false;
            }
            if (activeRun.compareAndSet(current, next)) {
                return true;
            }
        }
    }

    /**
     * 关联后台任务和 SSE emitter，便于停止时尽力取消并关闭连接。
     *
     * @param sessionId 会话 ID
     * @param future 后台任务
     * @param emitter SSE emitter
     */
    public void attachExecution(Long sessionId, Future<?> future, SseEmitter emitter) {
        ActiveRun run = run(sessionId);
        if (run != null) {
            run.attachExecution(future, emitter);
        }
    }

    /**
     * 记录用户消息已经落库。
     *
     * @param sessionId 会话 ID
     * @param userMessageId 用户消息 ID
     * @param createdAt 创建时间
     */
    public void userSaved(Long sessionId, Long userMessageId, Instant createdAt) {
        ActiveRun run = run(sessionId);
        if (run != null) {
            run.userSaved(userMessageId, createdAt);
            if (run.isStopped() && userMessageId != null) {
                stoppedUserMessages.add(userMessageId);
            }
        }
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
     * 记录助手消息已预创建但尚未生成最终内容。
     *
     * @param sessionId 会话 ID
     * @param assistantMessageId 助手消息 ID
     * @param updatedAt 更新时间
     */
    public void assistantReserved(Long sessionId, Long assistantMessageId, Instant updatedAt) {
        ActiveRun run = run(sessionId);
        if (run != null) {
            run.assistantReserved(assistantMessageId, updatedAt);
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
     * 停止当前活动轮次，并返回停止时的关键信息。
     *
     * @return 当前活动轮次；没有活动轮次时返回 null
     */
    public StoppedRun stopActive() {
        ActiveRun run = activeRun.get();
        if (run == null) {
            return null;
        }
        StoppedRun stopped = run.stop();
        if (stopped.userMessageId() != null) {
            stoppedUserMessages.add(stopped.userMessageId());
        }
        return stopped;
    }

    /**
     * 判断指定用户消息对应的运行是否已经被停止。
     *
     * @param userMessageId 用户消息 ID
     * @return 已停止返回 true
     */
    public boolean isStopped(Long userMessageId) {
        return userMessageId != null && stoppedUserMessages.contains(userMessageId);
    }

    /**
     * 判断当前会话或指定用户消息是否已经被停止。
     *
     * @param sessionId 会话 ID
     * @param userMessageId 用户消息 ID
     * @return 当前轮次已停止或用户消息已登记停止时返回 true
     */
    public boolean isStopped(Long sessionId, Long userMessageId) {
        ActiveRun run = run(sessionId);
        return (run != null && run.isStopped()) || isStopped(userMessageId);
    }

    /**
     * 为已停止的用户消息登记停止提示已保存，保证停止助手消息只落库一次。
     *
     * @param sessionId 会话 ID
     * @param userMessageId 用户消息 ID
     * @return 当前应保存停止提示时返回 true
     */
    public boolean markStoppedAssistantIfNeeded(Long sessionId, Long userMessageId) {
        return userMessageId != null
                && isStopped(sessionId, userMessageId)
                && stoppedAssistantSavedMessages.add(userMessageId);
    }

    /**
     * 获取某个会话当前活动轮次的只读快照。
     *
     * @param sessionId 会话 ID
     * @return 活动轮次快照；无活动轮次或已停止时返回 inactive
     */
    public ConversationRunStateDTO snapshot(Long sessionId) {
        ActiveRun run = run(sessionId);
        return run != null && !run.isStopped() ? run.snapshot() : ConversationRunStateDTO.inactive();
    }

    /**
     * 完成并移除某个活动轮次。
     *
     * @param sessionId 会话 ID
     * @param userMessageId 用户消息 ID，可为空
     */
    public void complete(Long sessionId, Long userMessageId) {
        ActiveRun run = run(sessionId);
        if (run != null && (userMessageId == null || userMessageId.equals(run.userMessageId()))) {
            activeRun.compareAndSet(run, null);
        }
    }

    /**
     * 清理当前活动轮次，通常用于会话重置。
     *
     * @param sessionId 会话 ID
     */
    public void clear(Long sessionId) {
        ActiveRun run = run(sessionId);
        if (run != null) {
            run.stop();
            complete(sessionId, run.userMessageId());
        }
    }

    private ActiveRun run(Long sessionId) {
        ActiveRun run = activeRun.get();
        return run != null && run.sessionId().equals(sessionId) ? run : null;
    }

    /**
     * 停止运行时返回的只读快照。
     *
     * @param sessionId 会话 ID
     * @param userMessageId 用户消息 ID
     * @param requestId 本轮请求 ID
     * @param assistantMessageId 已保存助手消息 ID
     */
    public record StoppedRun(Long sessionId, String requestId, Long userMessageId, Long assistantMessageId) {
    }

    private static final class ActiveRun {
        private final Long sessionId;
        private final String requestId;
        private final Instant startedAt;
        private final List<String> statuses = new ArrayList<>();
        private Long userMessageId;
        private Long assistantMessageId;
        private String assistantContent = "";
        private Instant updatedAt;
        private String error;
        private Future<?> future;
        private SseEmitter emitter;
        private boolean stopped;

        private ActiveRun(Long sessionId, String requestId, Instant startedAt) {
            this.sessionId = sessionId;
            this.requestId = requestId;
            this.startedAt = startedAt;
            this.updatedAt = startedAt;
        }

        private Long sessionId() {
            return sessionId;
        }

        private synchronized Long userMessageId() {
            return userMessageId;
        }

        private synchronized boolean isStopped() {
            return stopped;
        }

        private synchronized void attachExecution(Future<?> future, SseEmitter emitter) {
            this.future = future;
            this.emitter = emitter;
        }

        private synchronized void userSaved(Long userMessageId, Instant createdAt) {
            this.userMessageId = userMessageId;
            this.updatedAt = createdAt != null ? createdAt : Instant.now();
        }

        private synchronized void status(String status) {
            if (status == null || status.isBlank() || stopped) {
                return;
            }
            if (statuses.isEmpty() || !statuses.get(statuses.size() - 1).equals(status)) {
                statuses.add(status);
                while (statuses.size() > MAX_STATUSES) {
                    statuses.remove(0);
                }
            }
            updatedAt = Instant.now();
        }

        private synchronized void assistantSaved(Long assistantMessageId, String content, Instant updatedAt) {
            this.assistantMessageId = assistantMessageId;
            this.assistantContent = content != null ? content : "";
            this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
        }

        private synchronized void assistantReserved(Long assistantMessageId, Instant updatedAt) {
            this.assistantMessageId = assistantMessageId;
            this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
        }

        private synchronized void error(String message) {
            if (message == null || message.isBlank() || stopped) {
                return;
            }
            this.error = message;
            this.updatedAt = Instant.now();
        }

        private StoppedRun stop() {
            Future<?> futureToCancel;
            SseEmitter emitterToComplete;
            Long stoppedUserMessageId;
            Long stoppedAssistantMessageId;
            synchronized (this) {
                stopped = true;
                updatedAt = Instant.now();
                futureToCancel = future;
                emitterToComplete = emitter;
                stoppedUserMessageId = userMessageId;
                stoppedAssistantMessageId = assistantMessageId;
            }
            if (futureToCancel != null) {
                futureToCancel.cancel(true);
            }
            if (emitterToComplete != null) {
                try {
                    emitterToComplete.complete();
                } catch (Exception ignored) {
                    // The client may already be gone.
                }
            }
            return new StoppedRun(sessionId, requestId, stoppedUserMessageId, stoppedAssistantMessageId);
        }

        private synchronized ConversationRunStateDTO snapshot() {
            return new ConversationRunStateDTO(
                    true,
                    userMessageId,
                    assistantMessageId,
                    assistantContent,
                    List.copyOf(statuses),
                    startedAt,
                    updatedAt,
                    error
            );
        }
    }
}
