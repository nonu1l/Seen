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

    public WorkService(WorkRepository workRepo, RecordRepository recordRepo,
                       SubjectTypeRepository subjectTypeRepo, RecordStatusRepository recordStatusRepo,
                       BangumiService bangumiService) {
        this.workRepo          = workRepo;
        this.recordRepo        = recordRepo;
        this.subjectTypeRepo   = subjectTypeRepo;
        this.recordStatusRepo  = recordStatusRepo;
        this.bangumiService    = bangumiService;
    }

    public List<WorkListItem> listAll() {
        List<Work> works = workRepo.findAll();
        List<WorkListItem> results = new ArrayList<>();
        for (Work w : works) {
            try {
                WorkListItem it = buildListItem(w);
                if (it != null) results.add(it);
            } catch (Exception e) {
                log.warn("listAll item failed: {}", e.getMessage());
            }
        }
        return results;
    }

    /**
     * 以 Bangumi 远程结果为主，匹配本地标记状态。
     * 已标记的标注状态，未标记的保留供标记。
     */
    public SearchResponse search(String query) {
        List<WorkSearchResult> remote = bangumiService.search(query);

        List<Work> allWorks = workRepo.findAll();
        Set<Long> localIds = new HashSet<>();
        for (Work w : allWorks) {
            if (w.getId() != null) localIds.add(w.getId());
        }

        List<WorkListItem> local = new ArrayList<>();
        List<WorkSearchResult> works = new ArrayList<>();

        for (WorkSearchResult r : remote) {
            if (r.getId() != null && localIds.contains(r.getId())) {
                workRepo.findById(r.getId()).ifPresent(w -> {
                    WorkListItem it = buildListItem(w);
                    if (it != null) local.add(it);
                });
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
                WorkListItem it = buildListItem(w);
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
                d.setRewatched(watched > 1);
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
        Optional<Record> latest = recordRepo.findLatestByWorkId(id);

        if (latest.isPresent() && !latest.get().getStatus().equals(req.getStatus())) {
            Record r = latest.get();
            r.setStatus(req.getStatus());
            recordRepo.save(r);
        } else if (latest.isEmpty()) {
            Record r = new Record();
            r.setWorkId(id);
            r.setStatus(req.getStatus());
            r.setCreatedAt(Instant.now());
            recordRepo.save(r);
        }
        return buildListItem(work);
    }

    public WorkListItem rewatch(Long workId) {
        if (recordRepo.countByWorkIdAndStatus(workId, "collect") == 0)
            throw new IllegalStateException("rewatch requires existing watched record");
        Record r = new Record();
        r.setWorkId(workId);
        r.setStatus("collect");
        r.setCreatedAt(Instant.now());
        recordRepo.save(r);
        return buildListItem(workRepo.findById(workId).orElse(null));
    }

    public void unmark(Long workId) {
        recordRepo.deleteAllByWorkId(workId);
        workRepo.deleteById(workId);
    }

    public WorkListItem updateReview(Long workId, Double rating, String review) {
        Record r = recordRepo.findLatestByWorkId(workId)
                .orElseThrow(() -> new IllegalStateException("no record to update"));
        r.setRating(rating);
        r.setReview(review);
        recordRepo.save(r);
        return buildListItem(workRepo.findById(workId).orElse(null));
    }

    public String getCharacterName(Long id) {
        return bangumiService.getCharacterName(id);
    }

    public Map<String, Object> getDict() {
        return Map.of(
                "subjectTypes", subjectTypeRepo.findAll(),
                "recordStatuses", recordStatusRepo.findAll()
        );
    }

    // ── private helpers ────────────────────────────────────────────

    private WorkListItem buildListItem(Work w) {
        if (w == null) return null;
        try {
            WorkSearchResult b = bangumiService.getById(String.valueOf(w.getId()));
            WorkListItem it = new WorkListItem();
            it.setId(w.getId());
            it.setPlatform(w.getPlatform());
            if (b != null) {
                it.setNameOrig(b.getNameOrig());
                it.setNameCn(b.getNameCn());
                it.setCoverUrl(b.getCoverUrl());
                it.setYear(b.getYear());
                it.setTags(b.getTags());
                it.setPlot(b.getPlot());
                it.setScore(b.getScore());
            } else {
                it.setNameCn(w.getNameCn() != null ? w.getNameCn() : w.getName());
            }

            recordRepo.findLatestByWorkId(w.getId()).ifPresent(r -> {
                it.setStatus(r.getStatus());
                it.setMyRating(r.getRating());
                it.setMyReview(r.getReview());
                it.setLatestRecordAt(r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
            });
            long watched = recordRepo.countByWorkIdAndStatus(w.getId(), "collect");
            it.setRewatched(watched > 1);
            it.setRecordsCount((int) watched);
            return it;
        } catch (Exception e) {
            log.warn("buildListItem failed for workId={}: {}", w.getId(), e.getMessage());
            return null;
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
