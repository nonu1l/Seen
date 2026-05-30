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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class WorkService {

    private static final Logger log = LoggerFactory.getLogger(WorkService.class);

    private final WorkRepository            workRepo;
    private final RecordRepository          recordRepo;
    private final SubjectTypeRepository     subjectTypeRepo;
    private final RecordStatusRepository    recordStatusRepo;
    private final BangumiService            bangumiService;
    private final String                    bangumiProxy;

    public WorkService(WorkRepository workRepo, RecordRepository recordRepo,
                       SubjectTypeRepository subjectTypeRepo, RecordStatusRepository recordStatusRepo,
                       BangumiService bangumiService,
                       @Value("${seen.bangumi-proxy:}") String bangumiProxy) {
        this.workRepo          = workRepo;
        this.recordRepo        = recordRepo;
        this.subjectTypeRepo   = subjectTypeRepo;
        this.recordStatusRepo  = recordStatusRepo;
        this.bangumiService    = bangumiService;
        this.bangumiProxy      = bangumiProxy;
    }

    public List<WorkListItem> listAll() {
        List<Work> works = workRepo.findAllOrderByLatestRecord();
        if (works.isEmpty()) return List.of();

        // 批量加载最新 Record，替代逐条查询
        Set<Long> ids = new HashSet<>();
        for (Work w : works) ids.add(w.getId());
        Map<Long, Record> recordMap = new HashMap<>();
        for (Record r : recordRepo.findLatestByWorkIds(ids)) {
            recordMap.put(r.getWorkId(), r);
        }

        List<WorkListItem> results = new ArrayList<>();
        for (Work w : works) {
            try {
                WorkListItem it = buildListItem(w, recordMap.get(w.getId()));
                if (it != null) results.add(it);
            } catch (Exception e) {
                log.warn("listAll item failed workId={}: {}", w.getId(), e.getMessage());
            }
        }
        return results;
    }

    public SearchResponse search(String query) {
        List<WorkSearchResult> remote = bangumiService.search(query);

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

        List<WorkListItem> local = new ArrayList<>();
        List<WorkSearchResult> works = new ArrayList<>();

        for (WorkSearchResult r : remote) {
            if (r.getId() != null && localIds.contains(r.getId())) {
                Work w = workRepo.findById(r.getId()).orElse(null);
                if (w != null) {
                    WorkListItem it = buildListItem(w, recordMap.get(w.getId()));
                    if (it != null) local.add(it);
                }
            } else {
                works.add(r);
            }
        }

        // 补充本地模糊匹配到但 Bangumi 未返回的作品
        Set<Long> remoteIds = new HashSet<>();
        for (WorkSearchResult r : remote) {
            if (r.getId() != null) remoteIds.add(r.getId());
        }
        for (Work w : allWorks) {
            if (w.getId() != null && !remoteIds.contains(w.getId()) && matches(w, query)) {
                WorkListItem it = buildListItem(w, recordMap.get(w.getId()));
                if (it != null) local.add(it);
            }
        }

        return new SearchResponse(local, works);
    }

    public WorkDetail getDetail(String subjectId) {
        DetailedWork remote = bangumiService.getDetailed(subjectId);
        if (remote == null || remote.getBase() == null) return null;

        WorkDetail d = new WorkDetail();
        WorkSearchResult base = remote.getBase();
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

    public WorkListItem mark(MarkRequest req) {
        if (req.getId() == null || req.getStatus() == null)
            throw new IllegalArgumentException("id, status required");
        Long id = parseId(req.getId());
        if (id == null) throw new IllegalArgumentException("invalid id");

        Work work = upsertWork(id, req.getMeta());
        Record saved;

        Optional<Record> latest = recordRepo.findLatestByWorkId(id);
        if (latest.isPresent() && !latest.get().getStatus().equals(req.getStatus())) {
            Record r = latest.get();
            r.setStatus(req.getStatus());
            saved = recordRepo.save(r);
        } else if (latest.isEmpty()) {
            Record r = new Record();
            r.setWorkId(id);
            r.setStatus(req.getStatus());
            r.setCreatedAt(Instant.now());
            saved = recordRepo.save(r);
        } else {
            saved = latest.get();
        }

        return buildListItem(work, saved);
    }

    @Transactional
    public void unmark(Long workId) {
        recordRepo.deleteAllByWorkId(workId);
        workRepo.deleteById(workId);
    }

    /**
     * AI 模式标记：总是新增一条 record，保留历史记录可追溯。
     * 参数为 null 的字段自动从旧记录沿用（缺什么补什么）。
     * @return 包含新旧记录的上下文
     */
    public MarkResult markNew(MarkRequest req, Double rating, String review) {
        if (req.getId() == null)
            throw new IllegalArgumentException("id required");
        Long id = parseId(req.getId());
        if (id == null) throw new IllegalArgumentException("invalid id");

        Work work = upsertWork(id, req.getMeta());
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
        rec.setStatus(status);
        if (r != null) rec.setRating(r);
        if (rv != null && !rv.isEmpty()) rec.setReview(rv);
        recordRepo.save(rec);

        return new MarkResult(buildListItem(work, rec), previous);
    }

    @Transactional
    public void undoLastRecord(Long workId) {
        recordRepo.findLatestIdByWorkId(workId)
                .ifPresent(recordRepo::deleteRecordById);
    }

    public WorkListItem updateReview(Long workId, Double rating, String review) {
        Record r = recordRepo.findLatestByWorkId(workId)
                .orElseThrow(() -> new IllegalStateException("no record to update"));
        r.setRating(rating);
        r.setReview(review);
        recordRepo.save(r);
        Work w = workRepo.findById(workId).orElse(null);
        return buildListItem(w, r);
    }

    /**
     * AI 模式更新评分/影评：总是新建一条 record，保留历史。
     */
    public WorkListItem updateReviewNew(Long workId, Double rating, String review) {
        Record previous = recordRepo.findLatestByWorkId(workId)
                .orElseThrow(() -> new IllegalStateException("no record to update"));

        Record r = new Record();
        r.setWorkId(workId);
        r.setStatus(previous.getStatus());
        r.setRating(rating);
        r.setReview(review);
        recordRepo.save(r);

        return buildListItem(workRepo.findById(workId).orElse(null), r);
    }

    /** markNew 的返回值：新记录 + 旧记录（用于前端对比展示） */
    public record MarkResult(WorkListItem item, Record previousRecord) {}

    public String getCharacterName(Long id) {
        return bangumiService.getCharacterName(id);
    }

    public String getActorName(Long id) {
        return bangumiService.getPersonName(id);
    }

    public Map<String, Object> getDict() {
        return Map.of(
                "subjectTypes", subjectTypeRepo.findAll(),
                "recordStatuses", recordStatusRepo.findAll(),
                "bangumiProxy", bangumiProxy
        );
    }

    // ── private helpers ────────────────────────────────────────────

    /** 从 Work 实体 + Record 直接组装 WorkListItem，不再调用 Bangumi API */
    private WorkListItem buildListItem(Work w, Record latestRecord) {
        if (w == null) return null;
        WorkListItem it = new WorkListItem();
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
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(tagsJson, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    private Work upsertWork(Long id, WorkSearchResult meta) {
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
        if (meta != null) {
            applyMeta(w, meta);
        } else {
            WorkSearchResult fetched = bangumiService.getById(String.valueOf(id));
            if (fetched == null) throw new IllegalStateException("Subject not found: " + id);
            applyMeta(w, fetched);
        }
        w.setCreatedAt(now);
        w.setUpdatedAt(now);
        return workRepo.save(w);
    }

    private void applyMeta(Work w, WorkSearchResult meta) {
        if (meta.getNameOrig() != null) w.setName(meta.getNameOrig());
        if (meta.getNameCn() != null) w.setNameCn(meta.getNameCn());
        if (meta.getPlatform() != null) w.setPlatform(meta.getPlatform());
        if (meta.getCoverUrl() != null) w.setCoverUrl(meta.getCoverUrl());
        if (meta.getYear() != null) w.setYear(meta.getYear());
        if (meta.getPlot() != null) w.setPlot(meta.getPlot());
        if (meta.getScore() != null) w.setScore(meta.getScore());
        if (meta.getTags() != null && !meta.getTags().isEmpty()) {
            try {
                List<String> cleaned = ConversationService.cleanTags(meta.getTags(), meta.getPlatform());
                if (!cleaned.isEmpty()) {
                    w.setTagsCache(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(cleaned));
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

    private static Long parseId(String s) {
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
    }
}
