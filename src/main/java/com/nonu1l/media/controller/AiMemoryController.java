package com.nonu1l.media.controller;

import com.nonu1l.media.model.dto.AiMemoryDTO;
import com.nonu1l.media.service.AiPreferenceMemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 长期记忆后台接口。
 */
@RestController
@RequestMapping("/api/admin/ai-memory")
public class AiMemoryController {

    private static final Logger log = LoggerFactory.getLogger(AiMemoryController.class);

    private final AiPreferenceMemoryService memoryService;

    /**
     * 创建 AI 长期记忆后台控制器。
     *
     * @param memoryService 长期记忆服务
     */
    public AiMemoryController(AiPreferenceMemoryService memoryService) {
        this.memoryService = memoryService;
    }

    /**
     * 查看当前长期偏好画像。
     *
     * @return 当前画像，若尚未生成则 exists=false
     */
    @GetMapping
    public ResponseEntity<AiMemoryDTO> currentMemory() {
        try {
            return ResponseEntity.ok(memoryService.currentMemory());
        } catch (Exception e) {
            log.error("get ai memory failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 手动重建长期偏好画像。
     *
     * @return 重建后的画像；失败时返回旧画像
     */
    @PostMapping("/rebuild")
    public ResponseEntity<AiMemoryDTO> rebuildMemory() {
        try {
            return ResponseEntity.ok(memoryService.rebuildMemory());
        } catch (Exception e) {
            log.error("rebuild ai memory failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
