package com.nonu1l.media.service;

import com.nonu1l.media.agent.AgentService;
import com.nonu1l.media.agent.AgentRunEvents;
import com.nonu1l.media.agent.AgentRunListener;
import com.nonu1l.media.config.TokenUsageAdvisor;
import com.nonu1l.media.model.dto.*;
import com.nonu1l.media.model.entity.ConversationCard;
import com.nonu1l.media.model.entity.ConversationMessage;
import com.nonu1l.media.model.entity.ConversationSession;
import com.nonu1l.media.model.entity.Record;
import com.nonu1l.media.model.entity.Work;
import com.nonu1l.media.repository.ConversationCardRepository;
import com.nonu1l.media.repository.ConversationMessageRepository;
import com.nonu1l.media.repository.ConversationSessionRepository;
import com.nonu1l.media.repository.RecordRepository;
import com.nonu1l.media.repository.WorkRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * 对话服务：管理会话状态，组织用户消息到 Agent 的处理链路，并持久化 AI 回复与卡片动作。
 */
@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);
    private static final long STREAM_TIMEOUT_MILLIS = 10 * 60 * 1000L;

    private final ConversationSessionRepository sessionRepo;
    private final ConversationMessageRepository messageRepo;
    private final ConversationCardRepository cardRepo;
    private final AgentService agentService;
    private final WorkService workService;
    private final BangumiService bangumiService;
    private final RecordRepository recordRepo;
    private final WorkRepository workRepo;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final ConversationRunStore runStore;
    private volatile ExecutorService streamExecutor;

    /**
     * @param sessionRepo 会话数据仓储
     * @param messageRepo 消息数据仓储
     * @param cardRepo 卡片数据仓储
     * @param agentService 对话 Agent 调用服务
     * @param workService 作品标记/取消标记服务
     * @param bangumiService Bangumi 元数据服务
     * @param recordRepo 作品记录仓储
     * @param workRepo 作品仓储
     * @param transactionTemplate 事务模板，用于分段提交与避免长事务
     * @param objectMapper JSON 映射工具
     * @param runStore 当前进程内的活动对话轮次存储
     */
    public ConversationService(ConversationSessionRepository sessionRepo,
                                ConversationMessageRepository messageRepo,
                                ConversationCardRepository cardRepo,
                                AgentService agentService,
                                WorkService workService,
                                BangumiService bangumiService,
                                RecordRepository recordRepo,
                                WorkRepository workRepo,
                                TransactionTemplate transactionTemplate,
                                ObjectMapper objectMapper,
                                ConversationRunStore runStore) {
        this.sessionRepo = sessionRepo;
        this.messageRepo = messageRepo;
        this.cardRepo = cardRepo;
        this.agentService = agentService;
        this.workService = workService;
        this.bangumiService = bangumiService;
        this.recordRepo = recordRepo;
        this.workRepo = workRepo;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
        this.runStore = runStore;
    }

    // ── 会话管理 ─────────────────────────────────────────────

    /**
     * 获取当前可用会话；若不存在则创建新会话。
     *
     * @return 最近会话实体；若库中无会话则返回新建并持久化的会话
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
     * 查询当前会话的完整状态快照，供前端加载。
     *
     * @return 会话 ID、时间升序消息列表、卡片列表和活动轮次组成的状态对象
     */
    @Transactional(readOnly = true)
    public ConversationStateDTO getState() {
        ConversationSession session = getOrCreateSession();

        List<ConversationMessage> messages = messageRepo.findAllBySessionIdOrderByIdAsc(session.getId());
        List<ConversationMessageDTO> messageDTOs = messages.stream()
                .map(m -> new ConversationMessageDTO(m.getId(), m.getRole(), m.getContent(), m.getCreatedAt()))
                .toList();

        List<ConversationCard> cards = cardRepo.findAllBySessionIdAndCardStateInOrderByIdAsc(
                session.getId(), List.of("PENDING", "EDITABLE", "CONFLICT", "SAVED", "UNMARKED", "RESTORED"));
        List<ConversationCardDTO> cardDTOs = cards.stream()
                .map(this::toDTO)
                .toList();

        return new ConversationStateDTO(session.getId(), messageDTOs, cardDTOs, runStore.snapshot(session.getId()));
    }

    // ── 发送消息 ─────────────────────────────────────────────

    /**
     * 启动一轮 SSE 流式对话，后台执行 Agent 链路并持续推送事件。
     *
     * @param userInput 用户输入文本
     * @return 已启动的 SSE emitter
     */
    public SseEmitter sendMessageStream(String userInput) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        streamExecutor().execute(() -> doSendMessageStream(userInput, emitter));
        return emitter;
    }

    /**
     * 关闭流式对话线程池。
     */
    @PreDestroy
    public void shutdownStreamExecutor() {
        ExecutorService executor = streamExecutor;
        if (executor != null) {
            executor.shutdownNow();
        }
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

    private void doSendMessageStream(String userInput, SseEmitter emitter) {
        SseEventSender sender = new SseEventSender(emitter);
        ConversationSession session = getOrCreateSession();
        Long sessionId = session.getId();
        AgentRunListener listener = AgentRunEvents.streaming(event -> publishRunEvent(sessionId, event, sender));
        Instant now = Instant.now();

        try {
            ConversationMessage userMsg = transactionTemplate.execute(
                    tx -> saveUserMessage(session, userInput, now));
            if (userMsg != null) {
                runStore.start(sessionId, userMsg.getId(), userMsg.getCreatedAt());
                publishRunEvent(sessionId, AiStreamEventDTO.userSaved(userMsg.getId(), userMsg.getCreatedAt()), sender);
            }

            String history = buildHistory(session.getId());
            IntentAnalysisResultDTO result = invokeAgent(userInput, history, listener);
            String replyText = result.replyText() != null && !result.replyText().isBlank()
                    ? result.replyText()
                    : generateReplyText(result);
            if (!listener.hasDelta()) {
                streamReplyText(replyText, listener);
            }

            AssistantResponse response = transactionTemplate.execute(
                    tx -> saveAssistantResponse(session, result, userMsg, now));
            if (response != null) {
                publishRunEvent(sessionId, AiStreamEventDTO.assistantSaved(response.messageId(), response.replyText(), now), sender);
                if (response.cards() != null && !response.cards().isEmpty()) {
                    publishRunEvent(sessionId, AiStreamEventDTO.cards(response.cards()), sender);
                }
            }
            publishRunEvent(sessionId, AiStreamEventDTO.done(), sender);
            runStore.complete(sessionId);
            emitter.complete();
        } catch (Exception e) {
            log.error("sendMessageStream failed", e);
            publishRunEvent(sessionId, AiStreamEventDTO.error("抱歉，处理出错了，请重试。"), sender);
            runStore.complete(sessionId);
            emitter.complete();
        }
    }

    /**
     * 将流式事件同步写入活动轮次快照，并以尽力方式发送到 SSE 客户端。
     *
     * @param sessionId 会话 ID
     * @param event 流式事件
     * @param sender SSE 发送器
     */
    private void publishRunEvent(Long sessionId, AiStreamEventDTO event, SseEventSender sender) {
        if (event == null) {
            return;
        }
        switch (event.type()) {
            case "status" -> runStore.status(sessionId, event.content());
            case "delta" -> runStore.delta(sessionId, event.content());
            case "assistant_saved" -> runStore.assistantSaved(sessionId, event.messageId(), event.content(), event.createdAt());
            case "error" -> runStore.error(sessionId, event.content());
            default -> {
            }
        }
        sender.trySend(event);
    }

    /**
     * 调用 Agent 进行意图识别与结果归一化；失败时返回友好降级结果。
     *
     * @param userInput 用户输入
     * @param history 用于解析指代的最近会话历史文本
     * @return 归一化后的意图分析结果（reply、推荐卡片、取消标记 id）
     */
    private IntentAnalysisResultDTO invokeAgent(String userInput, String history) {
        return invokeAgent(userInput, history, AgentRunEvents.noop());
    }

    /**
     * 调用 Agent 并在需要时转发运行状态。
     *
     * @param userInput 用户输入
     * @param history 会话历史
     * @param listener 单轮运行监听器
     * @return 归一化后的意图分析结果
     */
    private IntentAnalysisResultDTO invokeAgent(String userInput, String history, AgentRunListener listener) {
        Long sessionId = getOrCreateSession().getId();
        TokenUsageAdvisor.setSession(sessionId);
        TokenUsageAdvisor.setCurrentTurn((int) messageRepo.countBySessionIdAndRole(sessionId, "user"));
        try {
            var agentState = agentService.invoke(userInput, history, listener);
            String replyText = agentState.replyText();
            if (replyText == null || replyText.isBlank()) {
                replyText = "正在处理你的请求...";
            }
            var cards = agentState.<MatchedEntryDTO>cards();
            var unmarkIds = agentState.<Long>unmarkIds();
            return new IntentAnalysisResultDTO(replyText, cards.isEmpty() ? null : cards,
                    unmarkIds.isEmpty() ? null : unmarkIds);
        } catch (Exception e) {
            log.error("Agent invoke failed", e);
            return new IntentAnalysisResultDTO("抱歉，处理出错了，请重试。", List.of(), List.of());
        } finally {
            TokenUsageAdvisor.clearSession();
        }
    }

    /**
     * 生成并持久化一条用户消息。
     *
     * @param session 所属会话
     * @param userInput 用户输入
     * @param now 统一时间戳
     * @return 已持久化的用户消息实体
     */
    private ConversationMessage saveUserMessage(ConversationSession session, String userInput, Instant now) {
        ConversationMessage userMsg = new ConversationMessage();
        userMsg.setSessionId(session.getId());
        userMsg.setRole("user");
        userMsg.setContent(userInput);
        userMsg.setCreatedAt(now);
        return messageRepo.save(userMsg);
    }

    /**
     * 在单次事务内写入助手回复、卡片实体、自动标记与取消标记快照。
     *
     * <p>如果 Agent 未返回回复文案，会基于匹配结果兜底生成。</p>
     *
     * @param session 当前会话
     * @param result Agent 解析结果
     * @param userMsg 本轮用户消息（用于关联回写）
     * @param now 统一时间戳
     * @return 含本轮助手消息 ID、文案、返回卡片列表的内部响应
     */
    private AssistantResponse saveAssistantResponse(ConversationSession session, IntentAnalysisResultDTO result,
                                                    ConversationMessage userMsg, Instant now) {
        String replyText = result.replyText() != null && !result.replyText().isBlank()
                ? result.replyText()
                : generateReplyText(result);

        ConversationMessage assistantMsg = new ConversationMessage();
        assistantMsg.setSessionId(session.getId());
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(replyText);
        assistantMsg.setCreatedAt(now);
        messageRepo.save(assistantMsg);

        // 保存卡片 + 自动保存
        List<ConversationCardDTO> cardDTOs = new ArrayList<>();
        if (result.entries() != null) {
            // 1) 先创建所有卡片实体
            List<ConversationCard> cards = new ArrayList<>();
            for (MatchedEntryDTO entry : result.entries()) {
                ConversationCard card = new ConversationCard();
                card.setSessionId(session.getId());
                card.setMessageId(assistantMsg.getId());
                card.setSubjectId(entry.subjectId());
                card.setNameCn(entry.nameCn());
                card.setRating(entry.rating());
                card.setReview(entry.comment());
                card.setStatus(entry.status());
                card.setCardState("PENDING");
                cards.add(card);
            }
            // 2) 并行补充元数据（Bangumi API 调用互不阻塞）
            cards.parallelStream().forEach(this::enrichCardMeta);
            // 3) 保存并自动标记
            for (ConversationCard card : cards) {
                cardRepo.save(card);
                cardDTOs.add(autoSaveCard(card));
            }
        }

        // 取消标记 — 删前快照到 UNMARK 卡片，可撤回
        if (result.unmarkIds() != null) {
            for (Long subjectId : result.unmarkIds()) {
                try {
                    ConversationCard card = snapshotBeforeUnmark(session.getId(), assistantMsg.getId(), subjectId);
                    workService.unmark(subjectId);
                    log.info("Auto-unmarked subjectId={}", subjectId);
                    if (card != null) cardDTOs.add(toDTO(card));
                } catch (Exception e) {
                    log.warn("Auto-unmark failed subjectId={}: {}", subjectId, e.getMessage());
                }
            }
        }

        session.setUpdatedAt(now);
        sessionRepo.save(session);

        return new AssistantResponse(assistantMsg.getId(), replyText, cardDTOs);
    }

    // ── 卡片操作 ─────────────────────────────────────────────

    /**
     * 保存/更新卡片建议：支持用户手工修改评分、评论、状态后落库标记。
     *
     * @param cardId 目标卡片 ID
     * @param req 入参可选字段：rating/review/status
     * @return 更新后的卡片 DTO（含历史对比信息）
     */
    @Transactional
    public ConversationCardDTO saveCard(Long cardId, SaveCardRequest req) {
        ConversationCard card = cardRepo.findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("Card not found: " + cardId));

        if (req != null) {
            if (req.rating() != null) card.setRating(req.rating());
            if (req.review() != null) card.setReview(req.review());
            if (req.status() != null) card.setStatus(req.status());
        }

        // UNMARK 卡片撤回 → 恢复标记但不改为普通卡片
        if ("UNMARKED".equals(card.getCardState())) {
            doSaveNew(card);
            card.setCardState("RESTORED");
            cardRepo.save(card);
            return toDTO(card);
        }

        return doSaveNew(card);
    }

    /**
     * 撤销 AI 标记：仅回滚该作品最近一条标记记录，并把卡片状态置为可编辑。
     *
     * @param cardId 卡片 ID
     * @return 撤销后的卡片 DTO
     */
    @Transactional
    public ConversationCardDTO undoCard(Long cardId) {
        ConversationCard card = cardRepo.findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("Card not found: " + cardId));

        try {
            workService.undoLastRecord(card.getSubjectId());
        } catch (Exception e) {
            log.warn("undoCard failed subjectId={}: {}", card.getSubjectId(), e.getMessage());
        }

        card.setCardState("EDITABLE");
        cardRepo.save(card);
        log.info("Card undone: cardId={} subjectId={}", cardId, card.getSubjectId());
        return toDTO(card);
    }

    /**
     * 自动保存入口：仅当卡片有状态时才调用外部标记；无状态表示纯推荐不落库状态。
     *
     * @param card 会话卡片实体
     * @return 卡片 DTO
     */
    private ConversationCardDTO autoSaveCard(ConversationCard card) {
        if (card.getStatus() == null) {
            return toDTO(card);
        }
        return doSaveNew(card);
    }

    /**
     * 发起一次“AI 标记”保存动作：生成 Mark 请求、补充元数据、写入 record，并更新卡片状态。
     *
     * <p>副作用：会创建新的标记记录，返回的 DTO 包含历史评分/评论/状态用于前端对比。</p>
     *
     * @param card 待保存卡片
     * @return 带历史对比字段的卡片 DTO
     */
    private ConversationCardDTO doSaveNew(ConversationCard card) {
        MarkRequest markReq = new MarkRequest();
        markReq.setId(String.valueOf(card.getSubjectId()));
        markReq.setStatus(card.getStatus()); // null = 沿用旧记录

        try {
            WorkSearchResultDTO meta = bangumiService.getById(String.valueOf(card.getSubjectId()));
            if (meta != null) markReq.setMeta(meta);
        } catch (Exception e) {
            log.warn("Failed to fetch Bangumi meta for subjectId={}: {}", card.getSubjectId(), e.getMessage());
        }

        Double rating = card.getRating() != null ? card.getRating() : null;
        WorkService.MarkResult mr = workService.markNew(markReq, rating, card.getReview());

        card.setCardState("SAVED");
        cardRepo.save(card);

        // 构建带历史对比的 DTO
        if (mr.previousRecord() != null) {
            Record p = mr.previousRecord();
            log.info("Card saved with history: subjectId={} old=[{} {}分 {}] new=[{} {}分 {}]",
                    card.getSubjectId(), p.getStatus(), p.getRating(), p.getReview(),
                    card.getStatus(), card.getRating(), card.getReview());
        }
        log.info("Card saved: cardId={} subjectId={}", card.getId(), card.getSubjectId());
        return toDTOWithHistory(card, mr.previousRecord());
    }

    /**
     * 将卡片实体与上一条记录组合成返回对象，用于前端显示“变更前后”对比。
     *
     * @param card 当前卡片
     * @param previous 上一条标记记录，可为 null
     * @return 含历史字段的卡片 DTO
     */
    private ConversationCardDTO toDTOWithHistory(ConversationCard card, Record previous) {
        return new ConversationCardDTO(
                card.getId(), card.getMessageId(), card.getSubjectId(),
                card.getNameCn(), card.getCoverUrl(), card.getYear(),
                card.getPlatform(),
                card.getRating(), card.getScore(), card.getReview(), card.getStatus(), card.getCardState(),
                deserializeTags(card.getTags()), card.getPlot(),
                previous != null ? previous.getRating() : null,
                previous != null ? previous.getReview() : null,
                previous != null ? previous.getStatus() : null
        );
    }

    /**
     * 清空并重建会话：删除最近会话所有消息与卡片后创建新会话。
     * 该操作会移除该会话历史记录，副作用为“会话上下文与卡片记录重置”。
     */
    @Transactional
    public void reset() {
        var latest = sessionRepo.findTopByOrderByIdDesc();
        if (latest.isPresent()) {
            ConversationSession session = latest.get();
            runStore.clear(session.getId());
            messageRepo.deleteAllBySessionId(session.getId());
            cardRepo.deleteAllBySessionId(session.getId());
            sessionRepo.delete(session);
        }
        // 创建新会话
        ConversationSession session = new ConversationSession();
        session.setCreatedAt(Instant.now());
        session.setUpdatedAt(Instant.now());
        sessionRepo.save(session);
        log.info("Conversation reset");
    }

    // ── 回复文案 ─────────────────────────────────────────────

    /**
     * 根据匹配结果生成中文回复文案。
     *
     * @param result Agent 意图分析结果
     * @return 可直接返回给前端的提示文本
     */
    static String generateReplyText(IntentAnalysisResultDTO result) {
        List<MatchedEntryDTO> entries = result.entries();
        if (entries == null || entries.isEmpty()) {
            return "未找到匹配条目，请检查剧名后重试。";
        }

        if (entries.size() == 1) {
            MatchedEntryDTO e = entries.getFirst();
            StringBuilder sb = new StringBuilder();
            sb.append("已匹配《").append(e.nameCn()).append("》");
            if (e.rating() != null) {
                sb.append("，评分 ").append(formatRating(e.rating())).append(" 分");
            }
            if (e.status() != null) {
                String label = switch (e.status()) {
                    case "wish" -> "想看";
                    case "doing" -> "在看";
                    case "collect" -> "看过";
                    case "on_hold" -> "搁置";
                    case "dropped" -> "抛弃";
                    default -> e.status();
                };
                sb.append("，标记为").append(label);
            }
            sb.append("。确认无误可点击保存。");
            return sb.toString();
        }

        // 多条目
        long tvCount = entries.stream().filter(e -> e.status() != null && !e.status().equals("wish")).count();
        StringBuilder sb = new StringBuilder();
        sb.append("已匹配 ").append(entries.size()).append(" 部作品");

        // 统计评分差异
        List<MatchedEntryDTO> withRating = entries.stream().filter(e -> e.rating() != null).toList();
        if (!withRating.isEmpty()) {
            double min = withRating.stream().mapToDouble(MatchedEntryDTO::rating).min().orElse(0);
            double max = withRating.stream().mapToDouble(MatchedEntryDTO::rating).max().orElse(0);
            if (Double.compare(min, max) == 0) {
                sb.append("，均分 ").append(formatRating(min)).append(" 分");
            } else {
                sb.append("，评分从 ").append(formatRating(min)).append(" 到 ").append(formatRating(max)).append(" 分不等");
            }
        }
        sb.append("。确认无误可逐张保存。");
        return sb.toString();
    }

    // ── helper ──────────────────────────────────────────────

    /**
     * 拼接最近会话历史为 LLM 可读文本，并附带最近助手输出的结构化卡片供指代解析。
     *
     * @param sessionId 会话 ID
     * @return 按时间顺序裁剪后的历史文本
     */
    private String buildHistory(Long sessionId) {
        List<ConversationMessage> msgs = messageRepo.findAllBySessionIdOrderByIdAsc(sessionId);
        if (msgs.isEmpty()) return "";

        // 加载本轮会话所有卡片，按消息 ID 分组
        List<ConversationCard> allCards = cardRepo.findAllBySessionIdAndCardStateInOrderByIdAsc(
                sessionId, List.of("PENDING", "EDITABLE", "CONFLICT", "SAVED", "UNMARKED", "RESTORED"));
        Map<Long, List<ConversationCard>> cardsByMsgId = new HashMap<>();
        for (var c : allCards) {
            cardsByMsgId.computeIfAbsent(c.getMessageId(), k -> new ArrayList<>()).add(c);
        }

        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, msgs.size() - 6);
        for (int i = start; i < msgs.size(); i++) {
            ConversationMessage m = msgs.get(i);
            sb.append(m.getRole().equals("user") ? "用户：" : "助手：");
            sb.append(m.getContent());
            sb.append("\n");

            // 为助手消息附加卡片结构化信息，便于 LLM 解析"第一个/第二个"等指代
            if ("assistant".equals(m.getRole())) {
                var cards = cardsByMsgId.get(m.getId());
                if (cards != null && !cards.isEmpty()) {
                    sb.append("[可标记条目: ");
                    for (int j = 0; j < cards.size(); j++) {
                        var c = cards.get(j);
                        if (j > 0) sb.append(", ");
                        sb.append(j + 1).append(".《").append(c.getNameCn())
                                .append("》(id=").append(c.getSubjectId()).append(")");
                    }
                    sb.append("]\n");
                }
            }
        }
        return sb.toString().trim();
    }

    /**
     * 将卡片实体转换为前端展示 DTO。
     *
     * @param card 卡片实体
     * @return 前端展示结构
     */
    private ConversationCardDTO toDTO(ConversationCard card) {
        return new ConversationCardDTO(
                card.getId(), card.getMessageId(), card.getSubjectId(),
                card.getNameCn(), card.getCoverUrl(), card.getYear(),
                card.getPlatform(),
                card.getRating(), card.getScore(), card.getReview(), card.getStatus(), card.getCardState(),
                deserializeTags(card.getTags()), card.getPlot()
        );
    }

    /**
     * 使用 Bangumi 数据补全卡片展示字段（封面、年份、平台、标签、简介、评分）。
     * 失败时仅保留原卡片内容，不影响主流程。
     *
     * @param card 待补全的卡片实体
     */
    private void enrichCardMeta(ConversationCard card) {
        try {
            WorkSearchResultDTO w = bangumiService.getById(String.valueOf(card.getSubjectId()));
            if (w != null) {
                if (w.getCoverUrl() != null) card.setCoverUrl(w.getCoverUrl());
                if (w.getYear() != null) card.setYear(w.getYear());
                if (w.getPlatform() != null) card.setPlatform(w.getPlatform());
                if (w.getTags() != null && !w.getTags().isEmpty()) {
                    card.setTags(serializeTags(cleanTags(w.getTags(), w.getPlatform())));
                }
                if (w.getPlot() != null) card.setPlot(w.getPlot());
                if (w.getScore() != null) card.setScore(w.getScore());
            }
        } catch (Exception e) {
            log.debug("Failed to enrich card meta subjectId={}: {}", card.getSubjectId(), e.getMessage());
        }
    }

    private String serializeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(tags);
        } catch (Exception e) {
            return String.join(",", tags);
        }
    }

    private List<String> deserializeTags(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            return List.of(json.split(","));
        }
    }

    /**
     * 清洗并去重标签：去除空字符串、去重、过滤与平台同名标签。
     *
     * @param tags 输入标签列表
     * @param platform 平台名
     * @return 过滤后的标签列表
     */
    static List<String> cleanTags(List<String> tags, String platform) {
        if (tags == null || tags.isEmpty()) return List.of();
        String plat = platform != null ? platform.trim() : "";
        return tags.stream()
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .filter(t -> plat.isEmpty() || !t.equalsIgnoreCase(plat))
                .distinct()
                .toList();
    }

    /**
     * 格式化 10 分制评分：整数不显示小数，小数保留 1 位。
     *
     * @param rating 评分，可为空
     * @return 适合展示给用户的评分文本
     */
    private static String formatRating(Double rating) {
        if (rating == null) {
            return "";
        }
        if (Math.rint(rating) == rating) {
            return String.valueOf(rating.intValue());
        }
        return String.format(java.util.Locale.ROOT, "%.1f", rating);
    }

    /**
     * 将已经生成好的回复按小块发送，用于结构化节点无法直接流式输出的场景。
     *
     * @param replyText 完整回复文本
     * @param listener 单轮运行监听器
     */
    private void streamReplyText(String replyText, AgentRunListener listener) {
        if (replyText == null || replyText.isBlank()) {
            return;
        }
        listener.status("正在生成回复");
        int chunkSize = 12;
        for (int i = 0; i < replyText.length(); i += chunkSize) {
            int end = Math.min(replyText.length(), i + chunkSize);
            listener.delta(replyText.substring(i, end));
            try {
                Thread.sleep(12);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * 取消标记前生成快照卡片：读取作品和最近一条记录，用于前端撤回与回显。
     *
     * @param sessionId 会话 ID
     * @param messageId 助手消息 ID
     * @param subjectId 作品 subjectId
     * @return 生成成功则返回快照卡片；失败则返回 null
     */
    private ConversationCard snapshotBeforeUnmark(Long sessionId, Long messageId, Long subjectId) {
        try {
            Work w = workRepo.findById(subjectId).orElse(null);
            if (w == null) return null;

            Record r = recordRepo.findLatestByWorkId(subjectId).orElse(null);

            ConversationCard card = new ConversationCard();
            card.setSessionId(sessionId);
            card.setMessageId(messageId);
            card.setSubjectId(subjectId);
            card.setNameCn(w.getNameCn() != null ? w.getNameCn() : w.getName());
            card.setCoverUrl(w.getCoverUrl());
            card.setYear(w.getYear());
            card.setPlatform(w.getPlatform());
            card.setStatus(r != null ? r.getStatus() : null);
            card.setRating(r != null ? r.getRating() : null);
            card.setReview(r != null ? r.getReview() : null);
            card.setCardState("UNMARKED");
            return cardRepo.save(card);
        } catch (Exception e) {
            log.warn("Failed to snapshot work before unmark subjectId={}: {}", subjectId, e.getMessage());
            return null;
        }
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
                // Client may have disconnected; nothing else to do for this stream.
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
     * 单轮流式处理落库后的内部响应，用于继续发送 assistant_saved 与 cards 事件。
     */
    private record AssistantResponse(Long messageId, String replyText, List<ConversationCardDTO> cards) {
    }
}
