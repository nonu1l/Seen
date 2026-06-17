package com.nonu1l.media.service;

import com.nonu1l.media.agent.AgentRunEvents;
import com.nonu1l.media.agent.AgentRunListener;
import com.nonu1l.media.agent.AutonomousAgentService;
import com.nonu1l.media.agent.tool.AiToolContextHolder;
import com.nonu1l.media.agent.tool.AiToolExecutionContext;
import com.nonu1l.media.config.TokenUsageAdvisor;
import com.nonu1l.media.model.dto.*;
import com.nonu1l.media.model.entity.ConversationCard;
import com.nonu1l.media.model.entity.ConversationMessage;
import com.nonu1l.media.model.entity.ConversationSession;
import com.nonu1l.media.repository.AiWorkSnapshotRepository;
import com.nonu1l.media.repository.ConversationCardRepository;
import com.nonu1l.media.repository.ConversationMessageRepository;
import com.nonu1l.media.repository.ConversationSessionRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

/**
 * 对话服务：管理单例 AI 对话运行、SSE 状态流和自主 Agent 工具执行结果。
 */
@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);
    private static final long STREAM_TIMEOUT_MILLIS = 10 * 60 * 1000L;
    private static final String STOPPED_REPLY = "已停止本次生成。";
    private static final String ERROR_REPLY = "抱歉，处理出错了，请重试。";

    private final ConversationSessionRepository sessionRepo;
    private final ConversationMessageRepository messageRepo;
    private final ConversationCardRepository cardRepo;
    private final AiWorkSnapshotRepository snapshotRepo;
    private final AutonomousAgentService autonomousAgentService;
    private final AiWorkOperationService operationService;
    private final TransactionTemplate transactionTemplate;
    private final ConversationRunStore runStore;
    private volatile ExecutorService streamExecutor;

    /**
     * 创建会话服务。
     *
     * @param sessionRepo 会话仓储
     * @param messageRepo 消息仓储
     * @param cardRepo 卡片仓储
     * @param snapshotRepo AI 快照仓储
     * @param autonomousAgentService 自主 Agent 服务
     * @param operationService AI 作品操作服务
     * @param transactionTemplate 事务模板
     * @param runStore 活动运行状态存储
     */
    public ConversationService(ConversationSessionRepository sessionRepo,
                               ConversationMessageRepository messageRepo,
                               ConversationCardRepository cardRepo,
                               AiWorkSnapshotRepository snapshotRepo,
                               AutonomousAgentService autonomousAgentService,
                               AiWorkOperationService operationService,
                               TransactionTemplate transactionTemplate,
                               ConversationRunStore runStore) {
        this.sessionRepo = sessionRepo;
        this.messageRepo = messageRepo;
        this.cardRepo = cardRepo;
        this.snapshotRepo = snapshotRepo;
        this.autonomousAgentService = autonomousAgentService;
        this.operationService = operationService;
        this.transactionTemplate = transactionTemplate;
        this.runStore = runStore;
    }

    /**
     * 获取当前会话；若不存在则创建。
     *
     * @return 最近会话
     */
    public ConversationSession getOrCreateSession() {
        var latest = sessionRepo.findTopByOrderByIdDesc();
        if (latest.isPresent()) return latest.get();

        ConversationSession session = new ConversationSession();
        session.setCreatedAt(Instant.now());
        session.setUpdatedAt(Instant.now());
        return sessionRepo.save(session);
    }

    /**
     * 查询当前会话状态。
     *
     * @return 当前消息、卡片和活动运行快照
     */
    @Transactional(readOnly = true)
    public ConversationStateDTO getState() {
        ConversationSession session = getOrCreateSession();

        List<ConversationMessageDTO> messageDTOs = messageRepo.findAllBySessionIdOrderByIdAsc(session.getId())
                .stream()
                .filter(m -> "user".equals(m.getRole()) || (m.getContent() != null && !m.getContent().isBlank()))
                .map(m -> new ConversationMessageDTO(m.getId(), m.getRole(), m.getContent(), m.getCreatedAt()))
                .toList();

        List<ConversationCardDTO> cardDTOs = cardRepo.findAllBySessionIdAndCardStateInOrderByIdAsc(
                        session.getId(), AiWorkOperationService.VISIBLE_CARD_STATES)
                .stream()
                .map(operationService::toDTO)
                .toList();

        return new ConversationStateDTO(session.getId(), messageDTOs, cardDTOs, runStore.snapshot(session.getId()));
    }

    /**
     * 启动一轮 SSE 对话。
     *
     * @param userInput 用户输入
     * @return SSE emitter
     */
    public SseEmitter sendMessageStream(String userInput) {
        ConversationSession session = getOrCreateSession();
        Long sessionId = session.getId();
        String requestId = UUID.randomUUID().toString();
        if (!runStore.reserve(sessionId, requestId, Instant.now())) {
            throw new ActiveConversationRunException("已有 AI 任务正在运行");
        }

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        try {
            Future<?> future = streamExecutor().submit(() -> doSendMessageStream(session, requestId, userInput, emitter));
            runStore.attachExecution(sessionId, future, emitter);
        } catch (RuntimeException e) {
            runStore.complete(sessionId, null);
            throw e;
        }
        return emitter;
    }

    /**
     * 停止当前活动 AI 轮次。
     *
     * @return 停止后的会话状态
     */
    public ConversationStateDTO stopActiveRun() {
        ConversationRunStore.StoppedRun stopped = runStore.stopActive();
        if (stopped != null && stopped.sessionId() != null && stopped.userMessageId() != null) {
            if (runStore.markStoppedAssistantIfNeeded(stopped.sessionId(), stopped.userMessageId())) {
                transactionTemplate.execute(tx -> {
                    saveStoppedAssistantResponse(stopped.sessionId(), stopped.requestId(), stopped.assistantMessageId());
                    return null;
                });
            }
            runStore.complete(stopped.sessionId(), stopped.userMessageId());
        }
        return getState();
    }

    /**
     * 保存或重新保存 AI 卡片。
     *
     * @param cardId 卡片 ID
     * @param req 保存参数
     * @return 更新后的卡片
     */
    public ConversationCardDTO saveCard(Long cardId, SaveCardRequest req) {
        return operationService.saveCard(cardId, req);
    }

    /**
     * 按 request 快照撤销 AI 卡片副作用。
     *
     * @param cardId 卡片 ID
     * @return 撤销后的卡片
     */
    public ConversationCardDTO undoCard(Long cardId) {
        return operationService.undoCard(cardId);
    }

    /**
     * 重置当前会话上下文与相关 AI 快照。
     */
    @Transactional
    public void reset() {
        var latest = sessionRepo.findTopByOrderByIdDesc();
        if (latest.isPresent()) {
            ConversationSession session = latest.get();
            runStore.clear(session.getId());
            snapshotRepo.deleteAllBySessionId(session.getId());
            cardRepo.deleteAllBySessionId(session.getId());
            messageRepo.deleteAllBySessionId(session.getId());
            sessionRepo.delete(session);
        }
        ConversationSession session = new ConversationSession();
        session.setCreatedAt(Instant.now());
        session.setUpdatedAt(Instant.now());
        sessionRepo.save(session);
    }

    /**
     * 关闭后台 SSE 线程池。
     */
    @PreDestroy
    public void shutdownStreamExecutor() {
        ExecutorService executor = streamExecutor;
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void doSendMessageStream(ConversationSession session, String requestId, String userInput, SseEmitter emitter) {
        SseEventSender sender = new SseEventSender(emitter);
        Long sessionId = session.getId();
        AgentRunListener listener = AgentRunEvents.streaming(event -> publishRunEvent(sessionId, event, sender));
        Instant startedAt = Instant.now();
        Long userMessageId = null;
        Long assistantMessageId = null;
        boolean doneSent = false;

        try {
            ConversationMessage userMsg = transactionTemplate.execute(
                    tx -> saveUserMessage(session, requestId, userInput, startedAt));
            if (userMsg == null) {
                throw new IllegalStateException("Failed to save user message");
            }
            userMessageId = userMsg.getId();
            runStore.userSaved(sessionId, userMessageId, userMsg.getCreatedAt());
            publishRunEvent(sessionId, AiStreamEventDTO.userSaved(userMessageId, userMsg.getCreatedAt()), sender);
            if (runStore.markStoppedAssistantIfNeeded(sessionId, userMessageId)) {
                transactionTemplate.execute(tx -> {
                    saveStoppedAssistantResponse(sessionId, requestId, null);
                    return null;
                });
                return;
            }
            if (runStore.isStopped(sessionId, userMessageId)) {
                return;
            }

            ConversationMessage assistantMsg = transactionTemplate.execute(
                    tx -> saveAssistantPlaceholder(session, requestId, startedAt));
            if (assistantMsg == null) {
                throw new IllegalStateException("Failed to save assistant message");
            }
            assistantMessageId = assistantMsg.getId();
            runStore.assistantReserved(sessionId, assistantMessageId, startedAt);

            String history = buildHistory(sessionId);
            TokenUsageAdvisor.setSession(sessionId);
            TokenUsageAdvisor.setRequestId(requestId);
            TokenUsageAdvisor.setCurrentTurn((int) messageRepo.countBySessionIdAndRole(sessionId, "user"));
            AiToolContextHolder.set(new AiToolExecutionContext(
                    sessionId, requestId, userMessageId, assistantMessageId, listener));

            String replyText;
            try {
                replyText = autonomousAgentService.invoke(userInput, history, listener);
            } finally {
                AiToolContextHolder.clear();
                TokenUsageAdvisor.clearSession();
            }

            if (runStore.isStopped(sessionId, userMessageId)) {
                return;
            }

            Instant completedAt = Instant.now();
            Long finalAssistantMessageId = assistantMessageId;
            transactionTemplate.execute(tx -> {
                updateAssistantMessage(finalAssistantMessageId, replyText, completedAt);
                touchSession(sessionId, completedAt);
                return null;
            });

            List<ConversationCardDTO> cards = operationService.cardsForRequest(sessionId, requestId);
            publishRunEvent(sessionId, AiStreamEventDTO.assistantSaved(assistantMessageId, replyText, completedAt), sender);
            if (!cards.isEmpty()) {
                publishRunEvent(sessionId, AiStreamEventDTO.cards(cards), sender);
            }
            publishRunEvent(sessionId, AiStreamEventDTO.done(), sender);
            doneSent = true;
        } catch (Exception e) {
            if (!runStore.isStopped(sessionId, userMessageId)) {
                log.error("sendMessageStream failed", e);
                Long finalAssistantMessageId = assistantMessageId;
                if (finalAssistantMessageId != null) {
                    transactionTemplate.execute(tx -> {
                        updateAssistantMessage(finalAssistantMessageId, ERROR_REPLY, Instant.now());
                        return null;
                    });
                }
                publishRunEvent(sessionId, AiStreamEventDTO.error(ERROR_REPLY), sender);
            }
        } finally {
            AiToolContextHolder.clear();
            TokenUsageAdvisor.clearSession();
            runStore.complete(sessionId, userMessageId);
            if (!doneSent && !runStore.isStopped(userMessageId)) {
                sender.trySend(AiStreamEventDTO.done());
            }
            emitter.complete();
        }
    }

    private ConversationMessage saveUserMessage(ConversationSession session, String requestId,
                                                String userInput, Instant now) {
        ConversationMessage userMsg = new ConversationMessage();
        userMsg.setSessionId(session.getId());
        userMsg.setRequestId(requestId);
        userMsg.setRole("user");
        userMsg.setContent(userInput);
        userMsg.setCreatedAt(now);
        session.setUpdatedAt(now);
        sessionRepo.save(session);
        return messageRepo.save(userMsg);
    }

    private ConversationMessage saveAssistantPlaceholder(ConversationSession session, String requestId, Instant now) {
        ConversationMessage assistantMsg = new ConversationMessage();
        assistantMsg.setSessionId(session.getId());
        assistantMsg.setRequestId(requestId);
        assistantMsg.setRole("assistant");
        assistantMsg.setContent("");
        assistantMsg.setCreatedAt(now);
        return messageRepo.save(assistantMsg);
    }

    private void updateAssistantMessage(Long assistantMessageId, String content, Instant now) {
        ConversationMessage assistantMsg = messageRepo.findById(assistantMessageId)
                .orElseThrow(() -> new IllegalArgumentException("Assistant message not found: " + assistantMessageId));
        assistantMsg.setContent(content != null ? content : "");
        messageRepo.save(assistantMsg);
    }

    private void saveStoppedAssistantResponse(Long sessionId, String requestId, Long assistantMessageId) {
        Instant now = Instant.now();
        if (assistantMessageId != null) {
            updateAssistantMessage(assistantMessageId, STOPPED_REPLY, now);
        } else {
            ConversationMessage assistantMsg = new ConversationMessage();
            assistantMsg.setSessionId(sessionId);
            assistantMsg.setRequestId(requestId);
            assistantMsg.setRole("assistant");
            assistantMsg.setContent(STOPPED_REPLY);
            assistantMsg.setCreatedAt(now);
            messageRepo.save(assistantMsg);
        }
        touchSession(sessionId, now);
    }

    private void touchSession(Long sessionId, Instant now) {
        sessionRepo.findById(sessionId).ifPresent(session -> {
            session.setUpdatedAt(now);
            sessionRepo.save(session);
        });
    }

    private void publishRunEvent(Long sessionId, AiStreamEventDTO event, SseEventSender sender) {
        if (event == null) {
            return;
        }
        switch (event.type()) {
            case "status" -> runStore.status(sessionId, event.content());
            case "assistant_saved" -> runStore.assistantSaved(sessionId, event.messageId(), event.content(), event.createdAt());
            case "error" -> runStore.error(sessionId, event.content());
            default -> {
            }
        }
        sender.trySend(event);
    }

    private ExecutorService streamExecutor() {
        ExecutorService executor = streamExecutor;
        if (executor == null) {
            synchronized (this) {
                executor = streamExecutor;
                if (executor == null) {
                    executor = Executors.newCachedThreadPool(new StreamThreadFactory());
                    streamExecutor = executor;
                }
            }
        }
        return executor;
    }

    private String buildHistory(Long sessionId) {
        List<ConversationMessage> msgs = messageRepo.findAllBySessionIdOrderByIdAsc(sessionId);
        if (msgs.isEmpty()) return "";

        List<ConversationCard> allCards = cardRepo.findAllBySessionIdAndCardStateInOrderByIdAsc(
                sessionId, AiWorkOperationService.VISIBLE_CARD_STATES);
        Map<Long, List<ConversationCard>> cardsByMsgId = new HashMap<>();
        for (var c : allCards) {
            cardsByMsgId.computeIfAbsent(c.getMessageId(), k -> new ArrayList<>()).add(c);
        }

        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, msgs.size() - 8);
        for (int i = start; i < msgs.size(); i++) {
            ConversationMessage m = msgs.get(i);
            if ("assistant".equals(m.getRole()) && (m.getContent() == null || m.getContent().isBlank())) {
                continue;
            }
            sb.append(m.getRole().equals("user") ? "用户：" : "助手：");
            sb.append(m.getContent());
            sb.append("\n");

            if ("assistant".equals(m.getRole())) {
                var cards = cardsByMsgId.get(m.getId());
                if (cards != null && !cards.isEmpty()) {
                    sb.append("[卡片: ");
                    for (int j = 0; j < cards.size(); j++) {
                        var c = cards.get(j);
                        if (j > 0) sb.append(", ");
                        sb.append(j + 1).append(".《").append(c.getNameCn())
                                .append("》(id=").append(c.getSubjectId())
                                .append(", state=").append(c.getCardState())
                                .append(", action=").append(c.getActionType()).append(")");
                    }
                    sb.append("]\n");
                }
            }
        }
        return sb.toString().trim();
    }

    private static final class SseEventSender {
        private final SseEmitter emitter;

        private SseEventSender(SseEmitter emitter) {
            this.emitter = emitter;
        }

        private synchronized void send(AiStreamEventDTO event) {
            try {
                emitter.send(SseEmitter.event().name(event.type()).data(event));
            } catch (IOException | IllegalStateException e) {
                throw new IllegalStateException("SSE send failed", e);
            }
        }

        private synchronized void trySend(AiStreamEventDTO event) {
            try {
                send(event);
            } catch (Exception ignored) {
                // Client may already be gone.
            }
        }
    }

    private static final class StreamThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "conversation-sse-stream");
            thread.setDaemon(true);
            return thread;
        }
    }

    /**
     * 表示当前进程内已有 AI 轮次正在运行。
     */
    public static class ActiveConversationRunException extends RuntimeException {
        public ActiveConversationRunException(String message) {
            super(message);
        }
    }
}
