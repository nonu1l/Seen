package com.nonu1l.media.controller;

import com.nonu1l.media.model.dto.*;
import com.nonu1l.media.service.WorkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/works")
public class WorksController {

    private static final Logger log = LoggerFactory.getLogger(WorksController.class);
    private final WorkService workService;

    public WorksController(WorkService workService) { this.workService = workService; }

    /**
     * 默认列表卡片显示
     */
    @PostMapping("/list")
    public ResponseEntity<List<WorkListItem>> list() {
        try { return ResponseEntity.ok(workService.listAll()); }
        catch (Exception e) { log.error("list failed", e); return ResponseEntity.internalServerError().build(); }
    }

    /**
     * 搜索
     *
     */
    @PostMapping("/search")
    public ResponseEntity<SearchResponse> search(@RequestBody Map<String, String> body) {
        try {
            String q = body.get("q");
            if (q == null || q.isBlank()) return ResponseEntity.ok(new SearchResponse(List.of(), List.of()));
            return ResponseEntity.ok(workService.search(q.trim()));
        } catch (Exception e) { log.error("search failed", e); return ResponseEntity.internalServerError().build(); }
    }


    /**
     * 详情 / 明细
     *
     */
    @PostMapping("/details")
    public ResponseEntity<WorkDetail> details(@RequestBody Map<String, String> body) {
        try {
            String id = body.get("id");
            if (id == null) return ResponseEntity.badRequest().build();
            WorkDetail d = workService.getDetail(id);
            return d == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(d);
        } catch (Exception e) { log.error("details failed", e); return ResponseEntity.internalServerError().build(); }
    }

    /**
     * 标记 -> 想看 在看 看过 搁置 抛弃
     *
     */
    @PostMapping("/mark")
    public ResponseEntity<WorkListItem> mark(@RequestBody MarkRequest req) {
        try { return ResponseEntity.ok(workService.mark(req)); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().build(); }
        catch (Exception e) { log.error("mark failed", e); return ResponseEntity.internalServerError().build(); }
    }

    //多刷接口，暂停开发这部分
    // @PostMapping("/rewatch")
    // public ResponseEntity<WorkListItem> rewatch(@RequestBody Map<String, Object> body) {
    //     try {
    //         Number workId = (Number) body.get("workId");
    //         if (workId == null) return ResponseEntity.badRequest().build();
    //         return ResponseEntity.ok(workService.rewatch(workId.longValue()));
    //     } catch (IllegalStateException e) { return ResponseEntity.status(409).build(); }
    //     catch (Exception e) { log.error("rewatch failed", e); return ResponseEntity.internalServerError().build(); }
    // }

    /**
     * 取消标记
     *
     */
    @PostMapping("/unmark")
    public ResponseEntity<Map<String, Boolean>> unmark(@RequestBody Map<String, Object> body) {
        try {
            Number workId = (Number) body.get("workId");
            if (workId == null) return ResponseEntity.badRequest().build();
            workService.unmark(workId.longValue());
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) { log.error("unmark failed", e); return ResponseEntity.internalServerError().build(); }
    }

    /**
     * 更新评分和评价
     */
    @PostMapping("/update-review")
    public ResponseEntity<WorkListItem> updateReview(@RequestBody Map<String, Object> body) {
        try {
            Number workId = (Number) body.get("workId");
            if (workId == null) return ResponseEntity.badRequest().build();
            Object ratingObj = body.get("rating");
            Double rating = ratingObj == null ? null : ((Number) ratingObj).doubleValue();
            String review = (String) body.get("review");
            return ResponseEntity.ok(workService.updateReview(workId.longValue(), rating, review));
        } catch (IllegalStateException e) { return ResponseEntity.status(409).build(); }
        catch (Exception e) { log.error("updateReview failed", e); return ResponseEntity.internalServerError().build(); }
    }

    /**
     * 获取角色中文名字
     */
    @PostMapping("/character-names")
    public ResponseEntity<Map<Long, String>> characterNames(@RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            List<Integer> raw = (List<Integer>) body.get("ids");
            if (raw == null || raw.isEmpty()) return ResponseEntity.ok(Map.of());
            Map<Long, String> result = new java.util.concurrent.ConcurrentHashMap<>();
            raw.stream().map(Number::longValue).parallel().forEach(id -> {
                String cn = workService.getCharacterName(id);
                if (cn != null && !cn.isEmpty()) result.put(id, cn);
            });
            return ResponseEntity.ok(result);
        } catch (Exception e) { log.error("characterNames failed", e); return ResponseEntity.internalServerError().build(); }
    }

    /**
     * 获取演员中文名字
     */
    @PostMapping("/actor-names")
    public ResponseEntity<Map<Long, String>> actorNames(@RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            List<Integer> raw = (List<Integer>) body.get("ids");
            if (raw == null || raw.isEmpty()) return ResponseEntity.ok(Map.of());
            Map<Long, String> result = new java.util.concurrent.ConcurrentHashMap<>();
            raw.stream().map(Number::longValue).parallel().forEach(id -> {
                String cn = workService.getActorName(id);
                if (cn != null && !cn.isEmpty()) result.put(id, cn);
            });
            return ResponseEntity.ok(result);
        } catch (Exception e) { log.error("actorNames failed", e); return ResponseEntity.internalServerError().build(); }
    }

    /**
     * 字典接口
     */
    @GetMapping("/dict")
    public ResponseEntity<Map<String, Object>> dict() {
        try { return ResponseEntity.ok(workService.getDict()); }
        catch (Exception e) { log.error("dict failed", e); return ResponseEntity.internalServerError().build(); }
    }
}
