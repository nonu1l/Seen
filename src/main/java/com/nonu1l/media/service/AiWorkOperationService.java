package com.nonu1l.media.service;

import com.nonu1l.media.agent.tool.AiToolContextHolder;
import com.nonu1l.media.agent.tool.AiToolExecutionContext;
import com.nonu1l.media.model.dto.ConversationCardDTO;
import com.nonu1l.media.model.dto.SaveCardRequest;
import com.nonu1l.media.model.dto.BangumiSubjectSummaryDTO;
import com.nonu1l.media.model.entity.AiWorkSnapshot;
import com.nonu1l.media.model.entity.ConversationCard;
import com.nonu1l.media.model.entity.Record;
import com.nonu1l.media.model.entity.Work;
import com.nonu1l.media.repository.AiWorkSnapshotRepository;
import com.nonu1l.media.repository.ConversationCardRepository;
import com.nonu1l.media.repository.RecordRepository;
import com.nonu1l.media.repository.WorkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 自主工具的作品操作服务：负责快照、卡片、记录写入和撤销恢复。
 */
@Service
public class AiWorkOperationService {

    public static final List<String> VISIBLE_CARD_STATES =
            List.of("PENDING", "SAVED", "EDITABLE", "UNMARKED", "RESTORED");

    private static final Logger log = LoggerFactory.getLogger(AiWorkOperationService.class);

    private final ConversationCardRepository cardRepo;
    private final AiWorkSnapshotRepository snapshotRepo;
    private final WorkRepository workRepo;
    private final RecordRepository recordRepo;
    private final BangumiService bangumiService;
    private final AiPreferenceMemoryService preferenceMemoryService;
    private final ObjectMapper objectMapper;

    /**
     * 创建 AI 作品操作服务。
     *
     * @param cardRepo 会话卡片仓储
     * @param snapshotRepo AI 快照仓储
     * @param workRepo 作品仓储
     * @param recordRepo 记录仓储
     * @param bangumiService Bangumi 元数据服务
     * @param preferenceMemoryService 长期偏好记忆服务
     * @param objectMapper JSON 映射器
     */
    public AiWorkOperationService(ConversationCardRepository cardRepo,
                                  AiWorkSnapshotRepository snapshotRepo,
                                  WorkRepository workRepo,
                                  RecordRepository recordRepo,
                                  BangumiService bangumiService,
                                  AiPreferenceMemoryService preferenceMemoryService,
                                  ObjectMapper objectMapper) {
        this.cardRepo = cardRepo;
        this.snapshotRepo = snapshotRepo;
        this.workRepo = workRepo;
        this.recordRepo = recordRepo;
        this.bangumiService = bangumiService;
        this.preferenceMemoryService = preferenceMemoryService;
        this.objectMapper = objectMapper;
    }

    /**
     * 展示一个候选作品卡片，不写入用户记录。
     *
     * @param subjectId Bangumi subjectId
     * @param reason 推荐或搜索理由，可为空
     * @return 创建后的展示卡片
     */
    @Transactional
    public ConversationCardDTO presentWork(Long subjectId, String reason) {
        AiToolExecutionContext context = AiToolContextHolder.require();
        context.listener().status("正在生成候选卡片");
        BangumiSubjectSummaryDTO meta = fetchMeta(subjectId);
        Record previous = recordRepo.findLatestByWorkId(subjectId).orElse(null);

        ConversationCard card = newCard(context, subjectId, "PRESENT", "PENDING");
        applyMeta(card, meta);
        if (reason != null && !reason.isBlank()) {
            card.setReview(reason.trim());
        }
        applyPrevious(card, previous);
        return toDTO(cardRepo.save(card));
    }

    /**
     * AI 直接标记或修改作品记录。
     *
     * @param subjectId Bangumi subjectId
     * @param status 目标状态，可为空，空时继承旧状态或默认 collect
     * @param rating 目标评分，可为空
     * @param review 目标影评，可为空
     * @param reason 操作原因，可为空，仅用于日志和工具返回说明
     * @return 保存后的卡片
     */
    @Transactional
    public ConversationCardDTO markWork(Long subjectId, String status, Double rating, String review, String reason) {
        AiToolExecutionContext context = AiToolContextHolder.require();
        context.listener().status("正在保存标记");
        BangumiSubjectSummaryDTO meta = fetchMeta(subjectId);
        ensureSnapshot(context, subjectId);
        Record previous = recordRepo.findLatestByWorkId(subjectId).orElse(null);
        Work work = upsertWork(subjectId, meta);

        String finalStatus = hasText(status) ? status.trim()
                : previous != null ? previous.getStatus() : "collect";
        Double finalRating = rating != null ? rating
                : previous != null ? previous.getRating() : null;
        String finalReview = review != null ? review
                : previous != null ? previous.getReview() : null;

        ConversationCard card = newCard(context, subjectId,
                previous == null ? "MARK" : "UPDATE", "SAVED");
        applyMeta(card, meta);
        if (!hasText(card.getNameCn()) && work != null) {
            card.setNameCn(displayName(work));
        }
        card.setStatus(finalStatus);
        card.setRating(finalRating);
        card.setReview(finalReview);
        applyPrevious(card, previous);
        card = cardRepo.save(card);

        Record record = new Record();
        record.setWorkId(subjectId);
        record.setRequestId(context.requestId());
        record.setCardId(card.getId());
        record.setCreatedBy("AI");
        record.setStatus(finalStatus);
        record.setRating(finalRating);
        if (hasText(finalReview)) {
            record.setReview(finalReview);
        }
        recordRepo.save(record);
        preferenceMemoryService.recordChanged(subjectId);
        log.info("AI markWork subjectId={} status={} rating={} reason={}", subjectId, finalStatus, finalRating, reason);
        return toDTO(card);
    }

    /**
     * AI 取消本地已有作品标记。
     *
     * @param subjectId Bangumi subjectId
     * @param reason 操作原因，可为空
     * @return 取消标记卡片；若本地无记录则返回 null
     */
    @Transactional
    public ConversationCardDTO unmarkWork(Long subjectId, String reason) {
        AiToolExecutionContext context = AiToolContextHolder.require();
        context.listener().status("正在取消标记");
        Work work = workRepo.findById(subjectId).orElse(null);
        if (work == null) {
            log.info("AI unmark skipped because work is missing subjectId={}", subjectId);
            return null;
        }
        ensureSnapshot(context, subjectId);
        Record previous = recordRepo.findLatestByWorkId(subjectId).orElse(null);

        ConversationCard card = newCard(context, subjectId, "UNMARK", "UNMARKED");
        applyWork(card, work);
        if (previous != null) {
            card.setStatus(previous.getStatus());
            card.setRating(previous.getRating());
            card.setReview(previous.getReview());
        }
        applyPrevious(card, previous);
        card = cardRepo.save(card);

        recordRepo.deleteAllByWorkId(subjectId);
        if (workRepo.existsById(subjectId)) {
            workRepo.deleteById(subjectId);
        }
        preferenceMemoryService.recordChanged(subjectId);
        log.info("AI unmarkWork subjectId={} reason={}", subjectId, reason);
        return toDTO(card);
    }

    /**
     * 保存用户手动确认或编辑后的 AI 卡片。
     *
     * @param cardId 卡片 ID
     * @param req 用户编辑后的字段，可为空
     * @return 保存后的卡片
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
        ensureSnapshot(card.getSessionId(), card.getRequestId(), card.getSubjectId());
        Record previous = recordRepo.findLatestByWorkId(card.getSubjectId()).orElse(null);
        upsertWorkFromCard(card);

        String finalStatus = hasText(card.getStatus()) ? card.getStatus()
                : previous != null ? previous.getStatus() : "collect";
        Double finalRating = card.getRating() != null ? card.getRating()
                : previous != null ? previous.getRating() : null;
        String finalReview = card.getReview() != null ? card.getReview()
                : previous != null ? previous.getReview() : null;

        card.setActionType("MANUAL_SAVE");
        card.setCardState("SAVED");
        card.setStatus(finalStatus);
        card.setRating(finalRating);
        card.setReview(finalReview);
        applyPrevious(card, previous);
        cardRepo.save(card);

        Record record = new Record();
        record.setWorkId(card.getSubjectId());
        record.setRequestId(card.getRequestId());
        record.setCardId(card.getId());
        record.setCreatedBy("USER");
        record.setStatus(finalStatus);
        record.setRating(finalRating);
        if (hasText(finalReview)) {
            record.setReview(finalReview);
        }
        recordRepo.save(record);
        preferenceMemoryService.recordChanged(card.getSubjectId());
        return toDTO(card);
    }

    /**
     * 按卡片所属 request 的操作前快照恢复作品状态。
     *
     * @param cardId 卡片 ID
     * @return 恢复后的卡片
     */
    @Transactional
    public ConversationCardDTO undoCard(Long cardId) {
        ConversationCard card = cardRepo.findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("Card not found: " + cardId));
        if ("PENDING".equals(card.getCardState())) {
            return toDTO(card);
        }
        restoreSnapshot(card.getSessionId(), card.getRequestId(), card.getSubjectId());
        card.setCardState("UNMARKED".equals(card.getCardState()) ? "RESTORED" : "EDITABLE");
        cardRepo.save(card);
        preferenceMemoryService.recordChanged(card.getSubjectId());
        return toDTO(card);
    }

    /**
     * 查询本轮请求创建的所有展示卡片。
     *
     * @param sessionId 会话 ID
     * @param requestId 请求 ID
     * @return 卡片 DTO 列表
     */
    @Transactional(readOnly = true)
    public List<ConversationCardDTO> cardsForRequest(Long sessionId, String requestId) {
        return cardRepo.findAllBySessionIdAndRequestIdOrderByIdAsc(sessionId, requestId)
                .stream().map(this::toDTO).toList();
    }

    /**
     * 将卡片实体转换为前端 DTO。
     *
     * @param card 卡片实体
     * @return 前端卡片 DTO
     */
    public ConversationCardDTO toDTO(ConversationCard card) {
        return new ConversationCardDTO(
                card.getId(), card.getMessageId(), card.getRequestId(), card.getSubjectId(), card.getActionType(),
                card.getNameCn(), card.getCoverUrl(), card.getYear(), card.getPlatform(),
                card.getRating(), card.getScore(), card.getReview(), card.getStatus(), card.getCardState(),
                deserializeTags(card.getTags()), card.getPlot(),
                card.getPreviousRating(), card.getPreviousReview(), card.getPreviousStatus(),
                card.getCreatedAt(), card.getUpdatedAt()
        );
    }

    private ConversationCard newCard(AiToolExecutionContext context, Long subjectId, String actionType, String state) {
        ConversationCard card = new ConversationCard();
        card.setSessionId(context.sessionId());
        card.setMessageId(context.assistantMessageId());
        card.setRequestId(context.requestId());
        card.setSubjectId(subjectId);
        card.setActionType(actionType);
        card.setCardState(state);
        return card;
    }

    private AiWorkSnapshot ensureSnapshot(AiToolExecutionContext context, Long subjectId) {
        return ensureSnapshot(context.sessionId(), context.requestId(), subjectId);
    }

    private AiWorkSnapshot ensureSnapshot(Long sessionId, String requestId, Long subjectId) {
        return snapshotRepo.findBySessionIdAndRequestIdAndSubjectId(sessionId, requestId, subjectId)
                .orElseGet(() -> createSnapshot(sessionId, requestId, subjectId));
    }

    private AiWorkSnapshot createSnapshot(Long sessionId, String requestId, Long subjectId) {
        try {
            AiWorkSnapshot snapshot = new AiWorkSnapshot();
            snapshot.setSessionId(sessionId);
            snapshot.setRequestId(requestId);
            snapshot.setSubjectId(subjectId);
            Work work = workRepo.findById(subjectId).orElse(null);
            snapshot.setWorkSnapshot(work != null ? objectMapper.writeValueAsString(workToMap(work)) : null);
            List<Record> records = recordRepo.findAllByWorkIdOrderByIdAsc(subjectId);
            snapshot.setRecordsSnapshot(objectMapper.writeValueAsString(records.stream()
                    .map(this::recordToMap).toList()));
            snapshot.setStatus("ACTIVE");
            return snapshotRepo.save(snapshot);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create AI work snapshot", e);
        }
    }

    private void restoreSnapshot(Long sessionId, String requestId, Long subjectId) {
        AiWorkSnapshot snapshot = snapshotRepo.findBySessionIdAndRequestIdAndSubjectId(sessionId, requestId, subjectId)
                .orElseThrow(() -> new IllegalStateException("Snapshot not found"));
        try {
            recordRepo.deleteAllByWorkId(subjectId);
            if (workRepo.existsById(subjectId)) {
                workRepo.deleteById(subjectId);
            }

            if (hasText(snapshot.getWorkSnapshot())) {
                JsonNode workNode = objectMapper.readTree(snapshot.getWorkSnapshot());
                Work work = new Work();
                work.setId(subjectId);
                work.setName(text(workNode, "name"));
                work.setNameCn(text(workNode, "nameCn"));
                work.setPlatform(text(workNode, "platform"));
                work.setCoverUrl(text(workNode, "coverUrl"));
                work.setYear(text(workNode, "year"));
                work.setPlot(text(workNode, "plot"));
                work.setScore(doubleValue(workNode, "score"));
                work.setTagsCache(text(workNode, "tagsCache"));
                work.setCreatedAt(instant(workNode, "createdAt"));
                work.setUpdatedAt(instant(workNode, "updatedAt"));
                workRepo.save(work);
            }

            if (hasText(snapshot.getRecordsSnapshot())) {
                JsonNode records = objectMapper.readTree(snapshot.getRecordsSnapshot());
                if (records != null && records.isArray()) {
                    for (JsonNode node : records) {
                        Record record = new Record();
                        record.setWorkId(subjectId);
                        record.setRequestId(text(node, "requestId"));
                        record.setCardId(longValue(node, "cardId"));
                        record.setCreatedBy(text(node, "createdBy"));
                        record.setStatus(text(node, "status"));
                        record.setRating(doubleValue(node, "rating"));
                        record.setReview(text(node, "review"));
                        record.setCreatedAt(instant(node, "createdAt"));
                        record.setUpdatedAt(instant(node, "updatedAt"));
                        if (hasText(record.getStatus())) {
                            recordRepo.save(record);
                        }
                    }
                }
            }
            snapshot.setStatus("RESTORED");
            snapshot.setRestoredAt(Instant.now());
            snapshotRepo.save(snapshot);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to restore AI work snapshot", e);
        }
    }

    private BangumiSubjectSummaryDTO fetchMeta(Long subjectId) {
        try {
            return bangumiService.getById(String.valueOf(subjectId));
        } catch (Exception e) {
            log.warn("Failed to fetch Bangumi meta subjectId={}: {}", subjectId, e.getMessage());
            return null;
        }
    }

    private Work upsertWork(Long subjectId, BangumiSubjectSummaryDTO meta) {
        Work work = workRepo.findById(subjectId).orElseGet(Work::new);
        work.setId(subjectId);
        if (meta != null) {
            applyMeta(work, meta);
        }
        if (work.getCreatedAt() == null) {
            work.setCreatedAt(Instant.now());
        }
        work.setUpdatedAt(Instant.now());
        return workRepo.save(work);
    }

    private Work upsertWorkFromCard(ConversationCard card) {
        Work work = workRepo.findById(card.getSubjectId()).orElseGet(Work::new);
        work.setId(card.getSubjectId());
        work.setNameCn(card.getNameCn());
        work.setCoverUrl(card.getCoverUrl());
        work.setYear(card.getYear());
        work.setPlatform(card.getPlatform());
        work.setScore(card.getScore());
        work.setPlot(card.getPlot());
        work.setTagsCache(card.getTags());
        if (work.getCreatedAt() == null) {
            work.setCreatedAt(Instant.now());
        }
        work.setUpdatedAt(Instant.now());
        return workRepo.save(work);
    }

    private void applyMeta(Work work, BangumiSubjectSummaryDTO meta) {
        if (meta.getNameOrig() != null) work.setName(meta.getNameOrig());
        if (meta.getNameCn() != null) work.setNameCn(meta.getNameCn());
        if (meta.getPlatform() != null) work.setPlatform(meta.getPlatform());
        if (meta.getCoverUrl() != null) work.setCoverUrl(meta.getCoverUrl());
        if (meta.getYear() != null) work.setYear(meta.getYear());
        if (meta.getPlot() != null) work.setPlot(meta.getPlot());
        if (meta.getScore() != null) work.setScore(meta.getScore());
        if (meta.getTags() != null) work.setTagsCache(serializeTags(cleanTags(meta.getTags(), meta.getPlatform())));
    }

    private void applyMeta(ConversationCard card, BangumiSubjectSummaryDTO meta) {
        if (meta == null) {
            Work work = workRepo.findById(card.getSubjectId()).orElse(null);
            if (work != null) applyWork(card, work);
            return;
        }
        card.setNameCn(meta.getNameCn() != null ? meta.getNameCn() : meta.getNameOrig());
        card.setCoverUrl(meta.getCoverUrl());
        card.setYear(meta.getYear());
        card.setPlatform(meta.getPlatform());
        card.setScore(meta.getScore());
        card.setPlot(meta.getPlot());
        card.setTags(serializeTags(cleanTags(meta.getTags(), meta.getPlatform())));
    }

    private void applyWork(ConversationCard card, Work work) {
        card.setNameCn(displayName(work));
        card.setCoverUrl(work.getCoverUrl());
        card.setYear(work.getYear());
        card.setPlatform(work.getPlatform());
        card.setScore(work.getScore());
        card.setPlot(work.getPlot());
        card.setTags(work.getTagsCache());
    }

    private void applyPrevious(ConversationCard card, Record previous) {
        card.setPreviousStatus(previous != null ? previous.getStatus() : null);
        card.setPreviousRating(previous != null ? previous.getRating() : null);
        card.setPreviousReview(previous != null ? previous.getReview() : null);
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
        if (!hasText(json)) return null;
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            return List.of(json.split(","));
        }
    }

    private static List<String> cleanTags(List<String> tags, String platform) {
        if (tags == null || tags.isEmpty()) return List.of();
        String plat = platform != null ? platform.trim() : "";
        return tags.stream()
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .filter(t -> plat.isEmpty() || !t.equalsIgnoreCase(plat))
                .distinct()
                .toList();
    }

    private java.util.Map<String, Object> workToMap(Work work) {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("id", work.getId());
        map.put("name", work.getName());
        map.put("nameCn", work.getNameCn());
        map.put("platform", work.getPlatform());
        map.put("coverUrl", work.getCoverUrl());
        map.put("year", work.getYear());
        map.put("plot", work.getPlot());
        map.put("score", work.getScore());
        map.put("tagsCache", work.getTagsCache());
        map.put("createdAt", work.getCreatedAt() != null ? work.getCreatedAt().toString() : null);
        map.put("updatedAt", work.getUpdatedAt() != null ? work.getUpdatedAt().toString() : null);
        return map;
    }

    private java.util.Map<String, Object> recordToMap(Record record) {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("id", record.getId());
        map.put("workId", record.getWorkId());
        map.put("requestId", record.getRequestId());
        map.put("cardId", record.getCardId());
        map.put("createdBy", record.getCreatedBy());
        map.put("status", record.getStatus());
        map.put("rating", record.getRating());
        map.put("review", record.getReview());
        map.put("createdAt", record.getCreatedAt() != null ? record.getCreatedAt().toString() : null);
        map.put("updatedAt", record.getUpdatedAt() != null ? record.getUpdatedAt().toString() : null);
        return map;
    }

    private static String displayName(Work work) {
        return work.getNameCn() != null && !work.getNameCn().isBlank() ? work.getNameCn() : work.getName();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String text(JsonNode node, String field) {
        return node != null && node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }

    private static Double doubleValue(JsonNode node, String field) {
        return node != null && node.has(field) && !node.get(field).isNull() ? node.get(field).asDouble() : null;
    }

    private static Long longValue(JsonNode node, String field) {
        return node != null && node.has(field) && !node.get(field).isNull() ? node.get(field).asLong() : null;
    }

    private static Instant instant(JsonNode node, String field) {
        String value = text(node, field);
        return hasText(value) ? Instant.parse(value) : null;
    }
}
