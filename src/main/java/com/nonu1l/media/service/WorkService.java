package com.nonu1l.media.service;

import com.nonu1l.media.model.dto.*;
import com.nonu1l.media.model.entity.Record;
import com.nonu1l.media.model.entity.Work;
import com.nonu1l.media.repository.RecordRepository;
import com.nonu1l.media.repository.RecordStatusRepository;
import com.nonu1l.media.repository.SubjectTypeRepository;
import com.nonu1l.media.repository.WorkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.*;

/**
 * 后端通用业务服务：统一处理作品检索、详情读取与进度标注的核心流程。
 *
 * <p>本服务负责本地作品与 Bangumi 元数据之间的组装与同步，外层 Controller
 * 依赖其返回模型进行展示和写回。</p>
 */
@Service
public class WorkService {

    private static final Logger log = LoggerFactory.getLogger(WorkService.class);

    private final WorkRepository            workRepo;
    private final RecordRepository          recordRepo;
    private final SubjectTypeRepository     subjectTypeRepo;
    private final RecordStatusRepository    recordStatusRepo;
    private final BangumiService            bangumiService;
    private final TransactionTemplate       transactionTemplate;
    private final SettingsService           settingsService;
    private final AiPreferenceMemoryService preferenceMemoryService;
    private final ObjectMapper              objectMapper;

    /**
     * 构造服务实例。
     *
     * @param workRepo 本地作品仓储
     * @param recordRepo 记录仓储
     * @param subjectTypeRepo 条目类型仓储
     * @param recordStatusRepo 进度状态仓储
     * @param bangumiService Bangumi API 服务
     * @param transactionTemplate 事务模板，用于把远程请求和数据库写入分离
     * @param settingsService 设置读取服务
     * @param preferenceMemoryService AI 长期偏好记忆服务
     * @param objectMapper JSON 映射工具
     */
    public WorkService(WorkRepository workRepo, RecordRepository recordRepo,
                       SubjectTypeRepository subjectTypeRepo, RecordStatusRepository recordStatusRepo,
                       BangumiService bangumiService,
                       TransactionTemplate transactionTemplate,
                       SettingsService settingsService,
                       AiPreferenceMemoryService preferenceMemoryService,
                       ObjectMapper objectMapper) {
        this.workRepo          = workRepo;
        this.recordRepo        = recordRepo;
        this.subjectTypeRepo   = subjectTypeRepo;
        this.recordStatusRepo  = recordStatusRepo;
        this.bangumiService    = bangumiService;
        this.transactionTemplate = transactionTemplate;
        this.settingsService   = settingsService;
        this.preferenceMemoryService = preferenceMemoryService;
        this.objectMapper      = objectMapper;
    }

    /**
     * 列出用户全部作品清单。
     *
     * <p>会优先加载每部作品的最新记录用于列表展示；单条作品组装失败时会记录告警并跳过，避免整体失败。</p>
     *
     * @return 过滤后的 {@link WorkListItemDTO} 列表
     */
    public List<WorkListItemDTO> listAll() {
        List<Work> works = workRepo.findAllOrderByLatestRecord();
        if (works.isEmpty()) return List.of();

        // 批量加载最新 Record，替代逐条查询
        Set<Long> ids = new HashSet<>();
        for (Work w : works) ids.add(w.getId());
        Map<Long, Record> recordMap = new HashMap<>();
        for (Record r : recordRepo.findLatestByWorkIds(ids)) {
            recordMap.put(r.getWorkId(), r);
        }

        List<WorkListItemDTO> results = new ArrayList<>();
        for (Work w : works) {
            try {
                WorkListItemDTO it = buildListItem(w, recordMap.get(w.getId()));
                if (it != null) results.add(it);
            } catch (Exception e) {
                log.warn("listAll item failed workId={}: {}", w.getId(), e.getMessage());
            }
        }
        return results;
    }

    /**
     * 按关键词搜索作品。
     *
     * <p>搜索结果分为本地已存在条目与远端 Bangumi 条目两个集合返回：
     * 先命中本地库的作品会按本地数据返回；否则返回远端候选。</p>
     *
     * @param query 用户输入关键词
     * @return {@link SearchDTO}，其中本地与远端两类结果会合并返回
     */
    public SearchDTO search(String query) {
        List<WorkSearchResultDTO> remote = bangumiService.search(query);

        List<Work> allWorks = workRepo.findAll();
        Set<Long> localIds = new HashSet<>();
        for (Work w : allWorks) {
            if (w.getId() != null) localIds.add(w.getId());
        }

        // 批量加载 Record
        Set<Long> ids = new HashSet<>();
        for (Work w : allWorks) ids.add(w.getId());
        Map<Long, Record> recordMap = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Record r : recordRepo.findLatestByWorkIds(ids)) {
                recordMap.put(r.getWorkId(), r);
            }
        }

        List<WorkListItemDTO> local = new ArrayList<>();
        List<WorkSearchResultDTO> works = new ArrayList<>();

        for (WorkSearchResultDTO r : remote) {
            if (r.getId() != null && localIds.contains(r.getId())) {
                Work w = workRepo.findById(r.getId()).orElse(null);
                if (w != null) {
                    WorkListItemDTO it = buildListItem(w, recordMap.get(w.getId()));
                    if (it != null) local.add(it);
                }
            } else {
                works.add(r);
            }
        }

        // 补充本地模糊匹配到但 Bangumi 未返回的作品
        Set<Long> remoteIds = new HashSet<>();
        for (WorkSearchResultDTO r : remote) {
            if (r.getId() != null) remoteIds.add(r.getId());
        }
        for (Work w : allWorks) {
            if (w.getId() != null && !remoteIds.contains(w.getId()) && matches(w, query)) {
                WorkListItemDTO it = buildListItem(w, recordMap.get(w.getId()));
                if (it != null) local.add(it);
            }
        }

        return new SearchDTO(local, works);
    }

    /**
     * 查询作品详情。
     *
     * <p>先读取 Bangumi 详情，再按本地作品ID补充状态、评分、影评和已看集数；未找到本地数据则返回纯远端字段。</p>
     *
     * @param subjectId 条目ID（字符串）
     * @return 包含详情信息的 {@link WorkDetailDTO}，若远端不存在则返回 {@code null}
     */
    public WorkDetailDTO getDetail(String subjectId) {
        DetailedWorkDTO remote = bangumiService.getDetailed(subjectId);
        if (remote == null || remote.getBase() == null) return null;

        WorkDetailDTO d = new WorkDetailDTO();
        WorkSearchResultDTO base = remote.getBase();
        d.setId(base.getId());
        d.setPlatform(base.getPlatform());
        d.setNameOrig(base.getNameOrig());
        d.setNameCn(base.getNameCn());
        d.setCoverUrl(base.getCoverUrl());
        d.setYear(base.getYear());
        d.setTags(base.getTags());
        d.setPlot(base.getPlot());
        d.setScore(base.getScore());
        d.setRegions(remote.getRegions());
        d.setEpisodes(remote.getEpisodes());
        d.setSeasonsCount(remote.getSeasonsCount());
        d.setRuntime(remote.getRuntime());
        d.setCast(remote.getCast());
        d.setImdbId(remote.getImdbId());

        Long wid = parseId(subjectId);
        if (wid != null) {
            workRepo.findById(wid).ifPresent(w -> {
                d.setId(wid);
                recordRepo.findLatestByWorkId(wid).ifPresent(r -> {
                    d.setStatus(r.getStatus());
                    d.setMyRating(r.getRating());
                    d.setMyReview(r.getReview());
                });
                long watched = recordRepo.countByWorkIdAndStatus(wid, "collect");
                d.setWatchedCount((int) watched);
            });
        }
        return d;
    }

    /**
     * 标记/更新单条作品状态（覆盖式）。
     *
     * <p>当最新记录状态与新状态不同会新增/更新一条记录；否则返回当前最新记录。
     * 该方法不会新增历史记录快照（与 {@link #markNew} 行为不同）。</p>
     *
     * @param req 标记参数，需包含作品ID与目标状态
     * @return 标记后的 {@link WorkListItemDTO}
     */
    public WorkListItemDTO mark(MarkRequest req) {
        if (req.getId() == null || req.getStatus() == null)
            throw new IllegalArgumentException("id, status required");
        Long id = parseId(req.getId());
        if (id == null) throw new IllegalArgumentException("invalid id");

        WorkSearchResultDTO meta = resolveMetaForInsert(id, req.getMeta());
        WorkListItemDTO item = Objects.requireNonNull(transactionTemplate.execute(tx -> markInTransaction(req, id, meta)));
        preferenceMemoryService.recordChanged(id);
        return item;
    }

    private WorkListItemDTO markInTransaction(MarkRequest req, Long id, WorkSearchResultDTO meta) {
        Work work = upsertWork(id, meta);
        Record saved;

        Optional<Record> latest = recordRepo.findLatestByWorkId(id);
        if (latest.isPresent() && !latest.get().getStatus().equals(req.getStatus())) {
            Record r = latest.get();
            r.setStatus(req.getStatus());
            saved = recordRepo.save(r);
        } else if (latest.isEmpty()) {
            Record r = new Record();
            r.setWorkId(id);
            r.setCreatedBy("USER");
            r.setStatus(req.getStatus());
            r.setCreatedAt(Instant.now());
            saved = recordRepo.save(r);
        } else {
            saved = latest.get();
        }

        return buildListItem(work, saved);
    }

    /**
     * 取消标记并清理作品相关记录。
     *
     * <p>删除该作品的全部进度记录，再删除作品本体，属于不可逆删除操作。</p>
     *
     * @param workId 作品ID
     */
    @Transactional
    public void unmark(Long workId) {
        recordRepo.deleteAllByWorkId(workId);
        workRepo.deleteById(workId);
        preferenceMemoryService.recordChanged(workId);
    }

    /**
     * AI 模式新增标记：每次都创建新记录。
     *
     * <p>用于需要保留全部历史的场景；当 req 中可选字段为空时会从上条记录继承并保持原字段。</p>
     *
     * @param req 标记请求，需至少包含作品ID
     * @param rating 可选评分，空时继承上一条评分
     * @param review 可选影评，空时继承上一条影评
     * @return 新建记录与旧记录的上下文结果
     */
    public MarkResult markNew(MarkRequest req, Double rating, String review) {
        if (req.getId() == null)
            throw new IllegalArgumentException("id required");
        Long id = parseId(req.getId());
        if (id == null) throw new IllegalArgumentException("invalid id");

        WorkSearchResultDTO meta = resolveMetaForInsert(id, req.getMeta());
        MarkResult result = Objects.requireNonNull(transactionTemplate.execute(tx -> markNewInTransaction(req, rating, review, id, meta)));
        preferenceMemoryService.recordChanged(id);
        return result;
    }

    private MarkResult markNewInTransaction(MarkRequest req, Double rating, String review, Long id, WorkSearchResultDTO meta) {
        Work work = upsertWork(id, meta);
        Record previous = recordRepo.findLatestByWorkId(id).orElse(null);

        // null / 空串字段从旧记录沿用
        String newStatus = (req.getStatus() != null && !req.getStatus().isBlank()) ? req.getStatus() : null;
        String status = newStatus != null ? newStatus
                : (previous != null ? previous.getStatus() : "collect");
        Double r = rating != null ? rating
                : (previous != null ? previous.getRating() : null);
        String rv = review != null ? review
                : (previous != null ? previous.getReview() : null);

        Record rec = new Record();
        rec.setWorkId(id);
        rec.setCreatedBy("AI");
        rec.setStatus(status);
        if (r != null) rec.setRating(r);
        if (rv != null && !rv.isEmpty()) rec.setReview(rv);
        recordRepo.save(rec);

        return new MarkResult(buildListItem(work, rec), previous);
    }

    /**
     * 回退最新一条记录。
     *
     * <p>仅删除该作品的最新记录，用于撤销最近一次操作。</p>
     *
     * @param workId 作品ID
     */
    @Transactional
    public void undoLastRecord(Long workId) {
        recordRepo.findLatestIdByWorkId(workId)
                .ifPresent(recordRepo::deleteRecordById);
        preferenceMemoryService.recordChanged(workId);
    }

    /**
     * 更新当前最新记录的评分与影评（覆盖式）。
     *
     * <p>该方法直接改写最新记录，不保留历史副本。</p>
     *
     * @param workId 作品ID
     * @param rating 评分，可为空
     * @param review 影评，可为空
     * @return 更新后的列表项
     */
    @Transactional
    public WorkListItemDTO updateReview(Long workId, Double rating, String review) {
        Record r = recordRepo.findLatestByWorkId(workId)
                .orElseThrow(() -> new IllegalStateException("no record to update"));
        r.setRating(rating);
        r.setReview(review);
        recordRepo.save(r);
        Work w = workRepo.findById(workId).orElse(null);
        WorkListItemDTO item = buildListItem(w, r);
        preferenceMemoryService.recordChanged(workId);
        return item;
    }

    /**
     * 标记返回值：返回新记录对应列表项及其前序记录，用于前端展示变更对比。
     */
    public record MarkResult(WorkListItemDTO item, Record previousRecord) {}

    /**
     * 查询角色中文名（透传 Bangumi API）。
     *
     * @param id 角色ID
     * @return 角色中文名，未命中返回 {@code null}
     */
    public String getCharacterName(Long id) {
        return bangumiService.getCharacterName(id);
    }

    /**
     * 查询人物中文名（透传 Bangumi API）。
     *
     * @param id 人物ID
     * @return 人物中文名，未命中返回 {@code null}
     */
    public String getActorName(Long id) {
        return bangumiService.getPersonName(id);
    }

    /**
     * 读取用于前端下拉或表单渲染的字典数据。
     *
     * @return 包含条目类型、记录状态及 Bangumi 代理配置的映射
     */
    public Map<String, Object> getDict() {
        return Map.of(
                "subjectTypes", subjectTypeRepo.findAll(),
                "recordStatuses", recordStatusRepo.findAll(),
                "bangumiProxy", settingsService.getString(SettingsService.BANGUMI_PROXY)
        );
    }

    // ── private helpers ────────────────────────────────────────────

    /** 从 Work 实体 + Record 直接组装 WorkListItemDTO，不再调用 Bangumi API */
    private WorkListItemDTO buildListItem(Work w, Record latestRecord) {
        if (w == null) return null;
        WorkListItemDTO it = new WorkListItemDTO();
        it.setId(w.getId());
        it.setPlatform(w.getPlatform());
        it.setNameOrig(w.getName());
        it.setNameCn(w.getNameCn() != null ? w.getNameCn() : w.getName());
        it.setCoverUrl(w.getCoverUrl());
        it.setYear(w.getYear());
        it.setPlot(w.getPlot());
        it.setScore(w.getScore());
        it.setTags(parseTags(w.getTagsCache()));

        if (latestRecord != null) {
            it.setStatus(latestRecord.getStatus());
            it.setMyRating(latestRecord.getRating());
            it.setMyReview(latestRecord.getReview());
            it.setLatestRecordAt(latestRecord.getCreatedAt() != null ? latestRecord.getCreatedAt().toString() : null);
        }
        long watched = recordRepo.countByWorkIdAndStatus(w.getId(), "collect");
        it.setRecordsCount((int) watched);
        return it;
    }

    @SuppressWarnings("unchecked")
    private List<String> parseTags(String tagsJson) {
        if (tagsJson == null || tagsJson.isBlank()) return List.of();
        try {
            return objectMapper.readValue(tagsJson, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    private Work upsertWork(Long id, WorkSearchResultDTO meta) {
        Optional<Work> existing = workRepo.findById(id);
        Instant now = Instant.now();
        if (existing.isPresent()) {
            Work w = existing.get();
            if (meta != null) applyMeta(w, meta);
            w.setUpdatedAt(now);
            return workRepo.save(w);
        }
        Work w = new Work();
        w.setId(id);
        if (meta == null) throw new IllegalStateException("Subject not found: " + id);
        applyMeta(w, meta);
        w.setCreatedAt(now);
        w.setUpdatedAt(now);
        return workRepo.save(w);
    }

    private WorkSearchResultDTO resolveMetaForInsert(Long id, WorkSearchResultDTO meta) {
        if (meta != null || workRepo.existsById(id)) return meta;
        WorkSearchResultDTO fetched = bangumiService.getById(String.valueOf(id));
        if (fetched == null) throw new IllegalStateException("Subject not found: " + id);
        return fetched;
    }

    private void applyMeta(Work w, WorkSearchResultDTO meta) {
        if (meta.getNameOrig() != null) w.setName(meta.getNameOrig());
        if (meta.getNameCn() != null) w.setNameCn(meta.getNameCn());
        if (meta.getPlatform() != null) w.setPlatform(meta.getPlatform());
        if (meta.getCoverUrl() != null) w.setCoverUrl(meta.getCoverUrl());
        if (meta.getYear() != null) w.setYear(meta.getYear());
        if (meta.getPlot() != null) w.setPlot(meta.getPlot());
        if (meta.getScore() != null) w.setScore(meta.getScore());
        if (meta.getTags() != null && !meta.getTags().isEmpty()) {
            try {
                List<String> cleaned = cleanTags(meta.getTags(), meta.getPlatform());
                if (!cleaned.isEmpty()) {
                    w.setTagsCache(objectMapper.writeValueAsString(cleaned));
                }
            } catch (Exception e) {
                log.debug("Failed to serialize tags: {}", e.getMessage());
            }
        }
    }

    private boolean matches(Work w, String query) {
        String q = query.toLowerCase();
        return (w.getName() != null && w.getName().toLowerCase().contains(q))
            || (w.getNameCn() != null && w.getNameCn().toLowerCase().contains(q));
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

    private static Long parseId(String s) {
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
    }
}
