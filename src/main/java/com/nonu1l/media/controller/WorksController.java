package com.nonu1l.media.controller;

import com.nonu1l.media.model.dto.*;
import com.nonu1l.media.service.WorkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 媒体作品相关接口：列表、搜索、详情、标记与字典查询。
 */
@RestController
@RequestMapping("/api/works")
public class WorksController {

    private static final Logger log = LoggerFactory.getLogger(WorksController.class);
    private final WorkService workService;

    /**
     * 创建控制器并注入作品服务。
     *
     * @param workService 作品领域服务，处理列表、搜索与状态更新。
     */
    public WorksController(WorkService workService) { this.workService = workService; }

    /**
     * 获取默认作品列表卡片。
     *
     * @return 按当前策略返回 {@link WorkListItemDTO} 列表；异常时返回 500。
     */
    @PostMapping("/list")
    public ResponseEntity<List<WorkListItemDTO>> list() {
        try { return ResponseEntity.ok(workService.listAll()); }
        catch (Exception e) { log.error("list failed", e); return ResponseEntity.internalServerError().build(); }
    }

    /**
     * 根据关键词检索作品。
     *
     * @param body 请求体，必须包含键 {@code q}，将对其做 trim 处理后检索。
     * @return 匹配结果；为空时返回空列表；异常时返回 500。
     */
    @PostMapping("/search")
    public ResponseEntity<SearchDTO> search(@RequestBody Map<String, String> body) {
        try {
            String q = body.get("q");
            if (q == null || q.isBlank()) return ResponseEntity.ok(new SearchDTO(List.of(), List.of()));
            return ResponseEntity.ok(workService.search(q.trim()));
        } catch (Exception e) { log.error("search failed", e); return ResponseEntity.internalServerError().build(); }
    }

    /**
     * 获取作品明细。
     *
     * @param body 请求体，必须包含作品 {@code id}。
     * @return 找到则返回明细；未找到返回 404；异常返回 500。
     */
    @PostMapping("/details")
    public ResponseEntity<WorkDetailDTO> details(@RequestBody Map<String, String> body) {
        try {
            String id = body.get("id");
            if (id == null) return ResponseEntity.badRequest().build();
            WorkDetailDTO d = workService.getDetail(id);
            return d == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(d);
        } catch (Exception e) { log.error("details failed", e); return ResponseEntity.internalServerError().build(); }
    }

    /**
     * 标记作品状态（想看、在看、看过、搁置、抛弃）。
     *
     * @param req 标记参数，包含作品标识与状态。
     * @return 更新后的作品条目；参数非法返回 400；业务冲突/异常返回 500。
     */
    @PostMapping("/mark")
    public ResponseEntity<WorkListItemDTO> mark(@RequestBody MarkRequest req) {
        try { return ResponseEntity.ok(workService.mark(req)); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().build(); }
        catch (Exception e) { log.error("mark failed", e); return ResponseEntity.internalServerError().build(); }
    }

    /**
     * 取消作品标记。
     *
     * @param body 请求体，必须包含 {@code workId}。
     * @return 成功返回确认对象 {@code {"ok": true}}；异常返回 500。
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
     * 更新作品评分与评价。
     *
     * @param body 请求体，需包含 {@code workId}，可选包含 {@code rating}/{@code review}。
     * @return 更新后的作品条目；冲突返回 409，异常返回 500。
     */
    @PostMapping("/update-review")
    public ResponseEntity<WorkListItemDTO> updateReview(@RequestBody Map<String, Object> body) {
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
     * 批量获取角色中文名。
     *
     * @param body 请求体，必须包含整数 ID 列表 {@code ids}。
     * @return 角色 ID 与中文名映射；仅返回命中结果；异常返回 500。
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
     * 批量获取演员中文名。
     *
     * @param body 请求体，必须包含演员 ID 列表 {@code ids}。
     * @return 演员 ID 与中文名映射；仅返回命中结果；异常返回 500。
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
     * 获取用于前端下拉与过滤的枚举字典。
     *
     * @return 作品相关静态字典数据，异常时返回 500。
     */
    @GetMapping("/dict")
    public ResponseEntity<Map<String, Object>> dict() {
        try { return ResponseEntity.ok(workService.getDict()); }
        catch (Exception e) { log.error("dict failed", e); return ResponseEntity.internalServerError().build(); }
    }
}
