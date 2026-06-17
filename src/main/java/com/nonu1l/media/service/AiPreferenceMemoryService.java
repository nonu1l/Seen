package com.nonu1l.media.service;

import com.nonu1l.media.model.dto.AiMemoryDTO;
import com.nonu1l.media.model.entity.Record;
import com.nonu1l.media.model.entity.UserPreferenceEvidence;
import com.nonu1l.media.model.entity.UserPreferenceMemory;
import com.nonu1l.media.model.entity.Work;
import com.nonu1l.media.repository.RecordRepository;
import com.nonu1l.media.repository.UserPreferenceEvidenceRepository;
import com.nonu1l.media.repository.UserPreferenceMemoryRepository;
import com.nonu1l.media.repository.WorkRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * AI 长期偏好记忆服务。
 *
 * <p>本服务只生成和读取可重建的用户画像，不修改 {@code work}/{@code record} 事实数据。</p>
 */
@Service
public class AiPreferenceMemoryService {

    private static final Logger log = LoggerFactory.getLogger(AiPreferenceMemoryService.class);
    private static final Long SINGLE_USER_MEMORY_ID = 1L;
    private static final int MAX_CONTEXT_LENGTH = 1200;
    private static final int MAX_EVIDENCE_FOR_LLM = 80;

    private final UserPreferenceMemoryRepository memoryRepo;
    private final UserPreferenceEvidenceRepository evidenceRepo;
    private final WorkRepository workRepo;
    private final RecordRepository recordRepo;
    private final AiChatClientFactory chatClientFactory;
    private final SettingsService settingsService;
    private final ObjectMapper objectMapper;
    private final String memoryPrompt;
    private final ScheduledExecutorService rebuildExecutor;

    private ScheduledFuture<?> pendingRebuild;

    /**
     * 创建长期偏好记忆服务。
     *
     * @param memoryRepo 画像仓储
     * @param evidenceRepo 画像证据仓储
     * @param workRepo 作品仓储
     * @param recordRepo 记录仓储
     * @param chatClientFactory AI 客户端工厂
     * @param settingsService 运行时设置服务
     * @param objectMapper JSON 映射工具
     */
    public AiPreferenceMemoryService(UserPreferenceMemoryRepository memoryRepo,
                                     UserPreferenceEvidenceRepository evidenceRepo,
                                     WorkRepository workRepo,
                                     RecordRepository recordRepo,
                                     AiChatClientFactory chatClientFactory,
                                     SettingsService settingsService,
                                     ObjectMapper objectMapper) {
        this.memoryRepo = memoryRepo;
        this.evidenceRepo = evidenceRepo;
        this.workRepo = workRepo;
        this.recordRepo = recordRepo;
        this.chatClientFactory = chatClientFactory;
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
        this.memoryPrompt = loadPrompt("prompts/preference-memory.st");
        this.rebuildExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "ai-preference-memory-rebuild");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 返回适合注入 Agent prompt 的短画像。
     *
     * @return 无画像或长期记忆关闭时返回空字符串
     */
    public String getMemoryContext() {
        if (!settingsService.getBoolean(SettingsService.AI_MEMORY_ENABLED)) {
            return "";
        }
        return memoryRepo.findById(SINGLE_USER_MEMORY_ID)
                .map(this::formatMemoryContext)
                .map(this::limitContext)
                .orElse("");
    }

    /**
     * 返回适合搜索节点使用的更短偏好提醒。
     *
     * @return 搜索节点可注入的偏好摘要；无画像时返回空字符串
     */
    public String getBriefMemoryContext() {
        String context = getMemoryContext();
        if (context.length() <= 400) {
            return context;
        }
        return context.substring(0, 400);
    }

    /**
     * 读取当前画像并转换为后台接口响应。
     *
     * @return 当前画像响应；没有画像时返回 exists=false
     */
    public AiMemoryDTO currentMemory() {
        return memoryRepo.findById(SINGLE_USER_MEMORY_ID)
                .map(this::toResponse)
                .orElseGet(() -> new AiMemoryDTO(false, null, null, null, null,
                        null, null, null, null));
    }

    /**
     * 从本地事实数据重建长期偏好画像。
     *
     * @return 重建后的画像；若无足够记录或 LLM 失败，返回当前可用画像
     */
    public AiMemoryDTO rebuildMemory() {
        if (!settingsService.getBoolean(SettingsService.AI_MEMORY_ENABLED)) {
            return currentMemory();
        }

        PreferenceSnapshot snapshot = buildPreferenceSnapshot();
        Optional<UserPreferenceMemory> existing = memoryRepo.findById(SINGLE_USER_MEMORY_ID);
        if (existing.isPresent() && snapshot.sourceHash().equals(existing.get().getSourceHash())) {
            log.debug("Skip preference memory rebuild because source hash is unchanged");
            return toResponse(existing.get());
        }

        if (snapshot.evidences().isEmpty()) {
            UserPreferenceMemory memory = existing.orElseGet(this::newMemory);
            memory.setVersion(nextVersion(existing));
            memory.setSummary("");
            memory.setLikesJson("[]");
            memory.setDislikesJson("[]");
            memory.setRecentShiftJson("[]");
            memory.setRecommendationRulesJson("[]");
            memory.setSourceHash(snapshot.sourceHash());
            memory.setUpdatedAt(Instant.now());
            evidenceRepo.deleteAll();
            return toResponse(memoryRepo.save(memory));
        }

        try {
            GeneratedMemory generated = generateMemory(snapshot.evidences());
            UserPreferenceMemory memory = existing.orElseGet(this::newMemory);
            memory.setVersion(nextVersion(existing));
            memory.setSummary(generated.summary());
            memory.setLikesJson(generated.likesJson());
            memory.setDislikesJson(generated.dislikesJson());
            memory.setRecentShiftJson(generated.recentShiftJson());
            memory.setRecommendationRulesJson(generated.recommendationRulesJson());
            memory.setSourceHash(snapshot.sourceHash());
            memory.setUpdatedAt(Instant.now());

            evidenceRepo.deleteAll();
            evidenceRepo.saveAll(snapshot.evidences());
            return toResponse(memoryRepo.save(memory));
        } catch (Exception e) {
            log.warn("Preference memory rebuild failed, keep old memory: {}", e.getMessage());
            return existing.map(this::toResponse)
                    .orElseGet(() -> new AiMemoryDTO(false, null, null, null, null,
                            null, null, null, null));
        }
    }

    /**
     * 记录变更后触发延迟重建。
     *
     * <p>该方法只安排后台任务，不阻塞作品标记、评分或取消标记主流程。</p>
     *
     * @param workId 发生变化的作品 ID，可用于日志定位
     */
    public synchronized void recordChanged(Long workId) {
        if (!shouldAutoRebuild()) {
            return;
        }
        if (pendingRebuild != null && !pendingRebuild.isDone()) {
            pendingRebuild.cancel(false);
        }
        pendingRebuild = rebuildExecutor.schedule(() -> {
            try {
                rebuildMemory();
            } catch (Exception e) {
                log.warn("Delayed preference memory rebuild failed workId={}: {}", workId, e.getMessage());
            }
        }, 20, TimeUnit.SECONDS);
    }

    /**
     * 关闭后台重建线程。
     */
    @PreDestroy
    public void shutdown() {
        rebuildExecutor.shutdownNow();
    }

    private boolean shouldAutoRebuild() {
        if (!settingsService.getBoolean(SettingsService.AI_ENABLED)
                || !settingsService.getBoolean(SettingsService.AI_MEMORY_ENABLED)) {
            return false;
        }
        SettingsService.AiRuntimeSetting setting = settingsService.currentRuntimeSetting();
        return hasText(setting.baseUrl()) && hasText(setting.apiKey()) && hasText(setting.model());
    }

    PreferenceSnapshot buildPreferenceSnapshot() {
        List<Work> works = workRepo.findAllOrderByLatestRecord();
        if (works.isEmpty()) {
            return new PreferenceSnapshot(List.of(), sha256("empty"));
        }

        Set<Long> workIds = new LinkedHashSet<>();
        for (Work work : works) {
            if (work.getId() != null) {
                workIds.add(work.getId());
            }
        }

        Map<Long, Record> latestRecords = new HashMap<>();
        for (Record record : recordRepo.findLatestByWorkIds(workIds)) {
            latestRecords.put(record.getWorkId(), record);
        }

        Set<Long> recentRecordIds = new LinkedHashSet<>();
        for (Record record : recordRepo.findTop30ByOrderByUpdatedAtDescIdDesc()) {
            if (record.getId() != null) {
                recentRecordIds.add(record.getId());
            }
        }

        List<EvidenceCandidate> candidates = new ArrayList<>();
        for (Work work : works) {
            Record record = latestRecords.get(work.getId());
            if (record == null) {
                continue;
            }
            addCandidateIfUseful(candidates, work, record, recentRecordIds.contains(record.getId()));
        }

        candidates.sort(Comparator.comparingDouble(EvidenceCandidate::weight).reversed());

        List<UserPreferenceEvidence> evidences = candidates.stream()
                .limit(MAX_EVIDENCE_FOR_LLM)
                .map(this::toEvidence)
                .toList();
        return new PreferenceSnapshot(evidences, sha256(buildSourceText(evidences)));
    }

    private void addCandidateIfUseful(List<EvidenceCandidate> candidates, Work work, Record record, boolean recent) {
        boolean hasReview = hasText(record.getReview());
        Double rating = meaningfulRating(record.getRating());
        String status = record.getStatus();

        if (rating != null && rating >= 8.0d) {
            candidates.add(new EvidenceCandidate(work, record, "high_rating", 5.0d + rating / 10.0d, recent));
        }
        if (rating != null && rating <= 5.0d) {
            candidates.add(new EvidenceCandidate(work, record, "low_rating", 5.0d + (10.0d - rating) / 10.0d, recent));
        }
        if (hasReview) {
            candidates.add(new EvidenceCandidate(work, record, "review", 4.0d + reviewWeight(record.getReview()), recent));
        }
        if ("dropped".equals(status) || "wish".equals(status)) {
            candidates.add(new EvidenceCandidate(work, record, "status", "dropped".equals(status) ? 4.5d : 3.0d, recent));
        }
        if (recent) {
            candidates.add(new EvidenceCandidate(work, record, "recent", 2.0d, true));
        }
    }

    private UserPreferenceEvidence toEvidence(EvidenceCandidate candidate) {
        UserPreferenceEvidence evidence = new UserPreferenceEvidence();
        evidence.setWorkId(candidate.work().getId());
        evidence.setRecordId(candidate.record().getId());
        evidence.setEvidenceType(candidate.type());
        evidence.setWeight(candidate.weight());
        evidence.setText(evidenceText(candidate));
        evidence.setCreatedAt(Instant.now());
        return evidence;
    }

    private String evidenceText(EvidenceCandidate candidate) {
        Work work = candidate.work();
        Record record = candidate.record();
        StringBuilder sb = new StringBuilder();
        sb.append("作品《").append(displayName(work)).append("》");
        if (hasText(work.getPlatform())) {
            sb.append("，类型=").append(work.getPlatform());
        }
        List<String> tags = parseTags(work.getTagsCache());
        if (!tags.isEmpty()) {
            sb.append("，标签=").append(String.join("/", tags));
        }
        Double rating = meaningfulRating(record.getRating());
        if (rating != null) {
            sb.append("，用户评分=").append(formatRating(rating));
        }
        if (hasText(record.getStatus())) {
            sb.append("，状态=").append(statusLabel(record.getStatus()));
        }
        if (hasText(record.getReview())) {
            sb.append("，影评=").append(trimTo(record.getReview(), 160));
        }
        if (candidate.recent()) {
            sb.append("，属于近期记录");
        }
        return sb.toString();
    }

    private GeneratedMemory generateMemory(List<UserPreferenceEvidence> evidences) throws Exception {
        ChatClient chatClient = chatClientFactory.currentClient();
        String content = chatClient.prompt()
                .system(memoryPrompt)
                .user(buildEvidencePrompt(evidences))
                .call()
                .content();
        if (!hasText(content)) {
            throw new IllegalStateException("LLM returned empty preference memory");
        }

        String json = IntentAnalysisService.extractJsonObject(content);
        JsonNode root = objectMapper.readTree(json);
        String summary = root.has("summary") ? root.get("summary").asText() : "";
        return new GeneratedMemory(
                trimTo(summary, 240),
                jsonArray(root, "likes"),
                jsonArray(root, "dislikes"),
                jsonArray(root, "recentShift"),
                jsonArray(root, "recommendationRules")
        );
    }

    private String buildEvidencePrompt(List<UserPreferenceEvidence> evidences) {
        StringBuilder sb = new StringBuilder();
        sb.append("以下是从本地记录中提取的证据，请生成长期偏好画像：\n");
        for (UserPreferenceEvidence evidence : evidences) {
            sb.append("- [").append(evidence.getEvidenceType())
                    .append(", weight=").append(String.format("%.1f", evidence.getWeight()))
                    .append("] ").append(evidence.getText()).append("\n");
        }
        return sb.toString();
    }

    String formatMemoryContext(UserPreferenceMemory memory) {
        if (!hasText(memory.getSummary())
                && !hasMeaningfulJson(memory.getLikesJson())
                && !hasMeaningfulJson(memory.getDislikesJson())
                && !hasMeaningfulJson(memory.getRecentShiftJson())
                && !hasMeaningfulJson(memory.getRecommendationRulesJson())) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        if (hasText(memory.getSummary())) {
            sb.append("用户长期偏好：").append(memory.getSummary()).append("\n");
        }
        appendJsonSection(sb, "喜欢", memory.getLikesJson());
        appendJsonSection(sb, "避雷", memory.getDislikesJson());
        appendJsonSection(sb, "近期变化", memory.getRecentShiftJson());
        appendJsonSection(sb, "推荐规则", memory.getRecommendationRulesJson());
        sb.append("请优先满足用户当前明确请求，不要让长期偏好覆盖当前条件。");
        return sb.toString().trim();
    }

    private void appendJsonSection(StringBuilder sb, String label, String json) {
        List<String> values = parseStringArray(json);
        if (!values.isEmpty()) {
            sb.append(label).append("：").append(String.join("；", values)).append("\n");
        }
    }

    private AiMemoryDTO toResponse(UserPreferenceMemory memory) {
        return new AiMemoryDTO(true, memory.getVersion(), memory.getSummary(),
                memory.getLikesJson(), memory.getDislikesJson(), memory.getRecentShiftJson(),
                memory.getRecommendationRulesJson(), memory.getSourceHash(), memory.getUpdatedAt());
    }

    private UserPreferenceMemory newMemory() {
        UserPreferenceMemory memory = new UserPreferenceMemory();
        memory.setId(SINGLE_USER_MEMORY_ID);
        memory.setVersion(0L);
        return memory;
    }

    private long nextVersion(Optional<UserPreferenceMemory> existing) {
        return existing.map(UserPreferenceMemory::getVersion).orElse(0L) + 1L;
    }

    private String buildSourceText(List<UserPreferenceEvidence> evidences) {
        if (evidences.isEmpty()) {
            return "empty";
        }
        StringBuilder sb = new StringBuilder();
        for (UserPreferenceEvidence evidence : evidences) {
            sb.append(evidence.getWorkId()).append('|')
                    .append(evidence.getRecordId()).append('|')
                    .append(evidence.getEvidenceType()).append('|')
                    .append(evidence.getWeight()).append('|')
                    .append(evidence.getText()).append('\n');
        }
        return sb.toString();
    }

    private String jsonArray(JsonNode root, String field) throws Exception {
        JsonNode value = root.get(field);
        if (value == null || !value.isArray()) {
            return "[]";
        }
        return objectMapper.writeValueAsString(value);
    }

    private List<String> parseStringArray(String json) {
        if (!hasText(json)) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node == null || !node.isArray()) {
                return List.of();
            }
            List<String> values = new ArrayList<>();
            for (JsonNode item : node) {
                String text = item.asText();
                if (hasText(text)) {
                    values.add(text);
                }
            }
            return values;
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<String> parseTags(String tagsJson) {
        return parseStringArray(tagsJson);
    }

    private String limitContext(String context) {
        if (context.length() <= MAX_CONTEXT_LENGTH) {
            return context;
        }
        return context.substring(0, MAX_CONTEXT_LENGTH);
    }

    private boolean hasMeaningfulJson(String json) {
        return !parseStringArray(json).isEmpty();
    }

    private String displayName(Work work) {
        if (hasText(work.getNameCn())) {
            return work.getNameCn();
        }
        if (hasText(work.getName())) {
            return work.getName();
        }
        return String.valueOf(work.getId());
    }

    private String statusLabel(String status) {
        return switch (status) {
            case "wish" -> "想看";
            case "doing" -> "在看";
            case "collect" -> "看过";
            case "on_hold" -> "搁置";
            case "dropped" -> "抛弃";
            default -> status;
        };
    }

    private String formatRating(Double rating) {
        if (rating == null) {
            return "";
        }
        if (Math.rint(rating) == rating) {
            return String.valueOf(rating.intValue());
        }
        return String.valueOf(rating);
    }

    /**
     * 将旧数据或模型误写入的 0 分视为未评分，避免长期记忆把“没打分”误判成负面偏好。
     *
     * @param rating 原始用户评分
     * @return 有效评分；未评分或非正数返回 {@code null}
     */
    private Double meaningfulRating(Double rating) {
        return rating != null && rating > 0.0d ? rating : null;
    }

    private double reviewWeight(String review) {
        if (!hasText(review)) {
            return 0.0d;
        }
        return Math.min(1.0d, review.length() / 120.0d);
    }

    private String trimTo(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String loadPrompt(String path) {
        try {
            return new String(new ClassPathResource(path).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load prompt: " + path, e);
        }
    }

    private String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(raw.hashCode());
        }
    }

    record EvidenceCandidate(Work work, Record record, String type, double weight, boolean recent) {
    }

    record PreferenceSnapshot(List<UserPreferenceEvidence> evidences, String sourceHash) {
    }

    record GeneratedMemory(String summary,
                           String likesJson,
                           String dislikesJson,
                           String recentShiftJson,
                           String recommendationRulesJson) {
    }
}
