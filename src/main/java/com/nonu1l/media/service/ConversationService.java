package com.nonu1l.media.service;

import com.nonu1l.media.agent.AgentService;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    private final ConversationSessionRepository sessionRepo;
    private final ConversationMessageRepository messageRepo;
    private final ConversationCardRepository cardRepo;
    private final AgentService agentService;
    private final WorkService workService;
    private final BangumiService bangumiService;
    private final RecordRepository recordRepo;
    private final WorkRepository workRepo;
    private final TransactionTemplate transactionTemplate;

    public ConversationService(ConversationSessionRepository sessionRepo,
                                ConversationMessageRepository messageRepo,
                                ConversationCardRepository cardRepo,
                                AgentService agentService,
                                WorkService workService,
                                BangumiService bangumiService,
                                RecordRepository recordRepo,
                                WorkRepository workRepo,
                                TransactionTemplate transactionTemplate) {
        this.sessionRepo = sessionRepo;
        this.messageRepo = messageRepo;
        this.cardRepo = cardRepo;
        this.agentService = agentService;
        this.workService = workService;
        this.bangumiService = bangumiService;
        this.recordRepo = recordRepo;
        this.workRepo = workRepo;
        this.transactionTemplate = transactionTemplate;
    }

    // ── 会话管理 ─────────────────────────────────────────────

    public ConversationSession getOrCreateSession() {
        List<ConversationSession> all = sessionRepo.findAll();
        if (!all.isEmpty()) return all.getLast();

        ConversationSession session = new ConversationSession();
        session.setCreatedAt(Instant.now());
        session.setUpdatedAt(Instant.now());
        return sessionRepo.save(session);
    }

    @Transactional(readOnly = true)
    public ConversationState getState() {
        ConversationSession session = getOrCreateSession();

        List<ConversationMessage> messages = messageRepo.findAllBySessionIdOrderByIdAsc(session.getId());
        List<ConversationMessageVO> messageVOs = messages.stream()
                .map(m -> new ConversationMessageVO(m.getId(), m.getRole(), m.getContent(), m.getCreatedAt()))
                .toList();

        List<ConversationCard> cards = cardRepo.findAllBySessionIdAndCardStateInOrderByIdAsc(
                session.getId(), List.of("PENDING", "EDITABLE", "CONFLICT", "SAVED", "UNMARKED", "RESTORED"));
        List<ConversationCardVO> cardVOs = cards.stream()
                .map(this::toVO)
                .toList();

        return new ConversationState(session.getId(), messageVOs, cardVOs);
    }

    // ── 发送消息 ─────────────────────────────────────────────

    /**
     * LLM 调用耗时长，拆出事务避免长时间锁 DB。
     * 步骤：存用户消息（事务）→ LLM 调用（无事务）→ 存回复/卡片/取消标记（事务）
     */
    public AiChatResponse sendMessage(String userInput) {
        ConversationSession session = getOrCreateSession();
        Instant now = Instant.now();

        ConversationMessage userMsg = transactionTemplate.execute(
                tx -> saveUserMessage(session, userInput, now));
        String history = buildHistory(session.getId());
        IntentAnalysisResult result = invokeAgent(userInput, history);

        return transactionTemplate.execute(
                tx -> saveAssistantResponse(session, result, userMsg, now));
    }

    private IntentAnalysisResult invokeAgent(String userInput, String history) {
        TokenUsageAdvisor.setSession(getOrCreateSession().getId());
        try {
            var agentState = agentService.invoke(userInput, history);
            String replyText = agentState.replyText();
            if (replyText == null || replyText.isBlank()) {
                replyText = "正在处理你的请求...";
            }
            var cards = agentState.<MatchedEntry>cards();
            var unmarkIds = agentState.<Long>unmarkIds();
            return new IntentAnalysisResult(replyText, cards.isEmpty() ? null : cards,
                    unmarkIds.isEmpty() ? null : unmarkIds);
        } catch (Exception e) {
            log.error("Agent invoke failed", e);
            return new IntentAnalysisResult("抱歉，处理出错了，请重试。", List.of(), List.of());
        } finally {
            TokenUsageAdvisor.clearSession();
        }
    }

    private ConversationMessage saveUserMessage(ConversationSession session, String userInput, Instant now) {
        ConversationMessage userMsg = new ConversationMessage();
        userMsg.setSessionId(session.getId());
        userMsg.setRole("user");
        userMsg.setContent(userInput);
        userMsg.setCreatedAt(now);
        return messageRepo.save(userMsg);
    }

    private AiChatResponse saveAssistantResponse(ConversationSession session, IntentAnalysisResult result,
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
        List<ConversationCardVO> cardVOs = new ArrayList<>();
        if (result.entries() != null) {
            // 1) 先创建所有卡片实体
            List<ConversationCard> cards = new ArrayList<>();
            for (MatchedEntry entry : result.entries()) {
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
                cardVOs.add(autoSaveCard(card));
            }
        }

        // 取消标记 — 删前快照到 UNMARK 卡片，可撤回
        if (result.unmarkIds() != null) {
            for (Long subjectId : result.unmarkIds()) {
                try {
                    ConversationCard card = snapshotBeforeUnmark(session.getId(), assistantMsg.getId(), subjectId);
                    workService.unmark(subjectId);
                    log.info("Auto-unmarked subjectId={}", subjectId);
                    if (card != null) cardVOs.add(toVO(card));
                } catch (Exception e) {
                    log.warn("Auto-unmark failed subjectId={}: {}", subjectId, e.getMessage());
                }
            }
        }

        session.setUpdatedAt(now);
        sessionRepo.save(session);

        return new AiChatResponse(assistantMsg.getId(), replyText, cardVOs);
    }

    // ── 卡片操作 ─────────────────────────────────────────────

    @Transactional
    public ConversationCardVO saveCard(Long cardId, SaveCardRequest req) {
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
            return toVO(card);
        }

        return doSaveNew(card);
    }

    /** 撤销 AI 标记：只删最新一条 record */
    @Transactional
    public ConversationCardVO undoCard(Long cardId) {
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
        return toVO(card);
    }

    /** 自动保存：无 status 的卡片仅为推荐，不自动保存 */
    private ConversationCardVO autoSaveCard(ConversationCard card) {
        if (card.getStatus() == null) {
            return toVO(card);
        }
        return doSaveNew(card);
    }

    /** AI 模式保存：总是新增 record，附带 history 信息供前端对比展示 */
    private ConversationCardVO doSaveNew(ConversationCard card) {
        MarkRequest markReq = new MarkRequest();
        markReq.setId(String.valueOf(card.getSubjectId()));
        markReq.setStatus(card.getStatus()); // null = 沿用旧记录

        try {
            WorkSearchResult meta = bangumiService.getById(String.valueOf(card.getSubjectId()));
            if (meta != null) markReq.setMeta(meta);
        } catch (Exception e) {
            log.warn("Failed to fetch Bangumi meta for subjectId={}: {}", card.getSubjectId(), e.getMessage());
        }

        Double rating = card.getRating() != null ? card.getRating().doubleValue() : null;
        WorkService.MarkResult mr = workService.markNew(markReq, rating, card.getReview());

        card.setCardState("SAVED");
        cardRepo.save(card);

        // 构建带历史对比的 VO
        if (mr.previousRecord() != null) {
            Record p = mr.previousRecord();
            log.info("Card saved with history: subjectId={} old=[{} {}分 {}] new=[{} {}分 {}]",
                    card.getSubjectId(), p.getStatus(), p.getRating(), p.getReview(),
                    card.getStatus(), card.getRating(), card.getReview());
        }
        log.info("Card saved: cardId={} subjectId={}", card.getId(), card.getSubjectId());
        return toVOWithHistory(card, mr.previousRecord());
    }

    private ConversationCardVO toVOWithHistory(ConversationCard card, Record previous) {
        return new ConversationCardVO(
                card.getId(), card.getMessageId(), card.getSubjectId(),
                card.getNameCn(), card.getCoverUrl(), card.getYear(),
                card.getPlatform(),
                card.getRating(), card.getScore(), card.getReview(), card.getStatus(), card.getCardState(),
                deserializeTags(card.getTags()), card.getPlot(),
                previous != null ? (previous.getRating() != null ? previous.getRating().intValue() : null) : null,
                previous != null ? previous.getReview() : null,
                previous != null ? previous.getStatus() : null
        );
    }

    @Transactional
    public void reset() {
        List<ConversationSession> all = sessionRepo.findAll();
        if (!all.isEmpty()) {
            ConversationSession session = all.getLast();
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

    static String generateReplyText(IntentAnalysisResult result) {
        List<MatchedEntry> entries = result.entries();
        if (entries == null || entries.isEmpty()) {
            return "未找到匹配条目，请检查剧名后重试。";
        }

        if (entries.size() == 1) {
            MatchedEntry e = entries.getFirst();
            StringBuilder sb = new StringBuilder();
            sb.append("已匹配《").append(e.nameCn()).append("》");
            if (e.rating() != null) {
                sb.append("，评分 ").append(e.rating()).append(" 分");
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
        List<MatchedEntry> withRating = entries.stream().filter(e -> e.rating() != null).toList();
        if (!withRating.isEmpty()) {
            int min = withRating.stream().mapToInt(MatchedEntry::rating).min().orElse(0);
            int max = withRating.stream().mapToInt(MatchedEntry::rating).max().orElse(0);
            if (min == max) {
                sb.append("，均分 ").append(min).append(" 分");
            } else {
                sb.append("，评分从 ").append(min).append(" 到 ").append(max).append(" 分不等");
            }
        }
        sb.append("。确认无误可逐张保存。");
        return sb.toString();
    }

    // ── helper ──────────────────────────────────────────────

    /** 將最近几轮对话拼接为历史文本，供 LLM 理解上下文 */
    private String buildHistory(Long sessionId) {
        List<ConversationMessage> msgs = messageRepo.findAllBySessionIdOrderByIdAsc(sessionId);
        if (msgs.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        // 最多取最近 6 条
        int start = Math.max(0, msgs.size() - 6);
        for (int i = start; i < msgs.size(); i++) {
            ConversationMessage m = msgs.get(i);
            sb.append(m.getRole().equals("user") ? "用户：" : "助手：");
            sb.append(m.getContent());
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private ConversationCardVO toVO(ConversationCard card) {
        return new ConversationCardVO(
                card.getId(), card.getMessageId(), card.getSubjectId(),
                card.getNameCn(), card.getCoverUrl(), card.getYear(),
                card.getPlatform(),
                card.getRating(), card.getScore(), card.getReview(), card.getStatus(), card.getCardState(),
                deserializeTags(card.getTags()), card.getPlot()
        );
    }

    /** 从 Bangumi 补充封面、年份、平台、标签、简介 */
    private void enrichCardMeta(ConversationCard card) {
        try {
            WorkSearchResult w = bangumiService.getById(String.valueOf(card.getSubjectId()));
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
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(tags);
        } catch (Exception e) {
            return String.join(",", tags);
        }
    }

    private List<String> deserializeTags(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, List.class);
        } catch (Exception e) {
            return List.of(json.split(","));
        }
    }

    /** 去重 + 过滤空白 + 排除与 platform 相同的标签 */
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

    /** 取消标记前快照：读取 work + 最新 record 数据，生成 UNMARK 卡片供撤回 */
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
            card.setRating(r != null ? (r.getRating() != null ? r.getRating().intValue() : null) : null);
            card.setReview(r != null ? r.getReview() : null);
            card.setCardState("UNMARKED");
            return cardRepo.save(card);
        } catch (Exception e) {
            log.warn("Failed to snapshot work before unmark subjectId={}: {}", subjectId, e.getMessage());
            return null;
        }
    }
}
