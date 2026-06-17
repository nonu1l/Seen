package com.nonu1l.media.controller;

import com.nonu1l.media.model.dto.*;
import com.nonu1l.media.service.ConversationService;
import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 会话相关的 HTTP 接口，负责查询会话状态、发送消息、卡片操作及会话重置。
 */
@RestController
@RequestMapping("/api/conversation")
public class ConversationController {

    private static final Logger log = LoggerFactory.getLogger(ConversationController.class);
    private final ConversationService conversationService;

    /**
     * 注入会话服务依赖。
     *
     * @param conversationService 会话领域服务，封装会话状态与消息处理逻辑。
     */
    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    /**
     * 获取当前会话状态。
     *
     * @return 成功时返回 {@link ConversationStateDTO}，失败时返回 500。
     */
    @GetMapping("/state")
    public ResponseEntity<ConversationStateDTO> getState() {
        try {
            return ResponseEntity.ok(conversationService.getState());
        } catch (Exception e) {
            log.error("getState failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 发送一条用户输入并以 SSE 形式流式返回 AI 处理进度和回复。
     *
     * @param req 用户输入请求，{@code userInput} 不能为空或空白。
     * @return 成功返回 SSE emitter；输入无效返回 400；已有任务运行返回 409；启动失败返回 500。
     */
    @PostMapping(value = "/send-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<?> sendStream(@RequestBody AiChatRequest req) {
        try {
            if (req.userInput() == null || req.userInput().isBlank()) {
                return ResponseEntity.badRequest().build();
            }
            return ResponseEntity.ok(conversationService.sendMessageStream(req.userInput().trim()));
        } catch (ConversationService.ActiveConversationRunException e) {
            return ResponseEntity.status(409)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("sendStream failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 停止当前正在运行的 AI 对话任务。
     *
     * @return 停止后的最新会话状态；没有活动任务时也返回当前状态。
     */
    @PostMapping("/stop")
    public ResponseEntity<ConversationStateDTO> stop() {
        try {
            return ResponseEntity.ok(conversationService.stopActiveRun());
        } catch (Exception e) {
            log.error("stop conversation failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 保存一张对话卡片。
     *
     * @param id  会话卡片主键 ID。
     * @param req 保存参数，可选，用于覆盖卡片标题与备注等字段。
     * @return 成功返回更新后的卡片；参数非法或不存在则返回 400；其他异常返回 500。
     */
    @PostMapping("/cards/{id}/save")
    public ResponseEntity<ConversationCardDTO> saveCard(@PathVariable Long id,
                                                        @RequestBody(required = false) SaveCardRequest req) {
        try {
            return ResponseEntity.ok(conversationService.saveCard(id, req));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("saveCard failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 回滚最近一次卡片保存操作。
     *
     * @param id 卡片 ID。
     * @return 成功返回回滚后的卡片；失败则返回 400 或 500。
     */
    @PostMapping("/cards/{id}/undo")
    public ResponseEntity<ConversationCardDTO> undoCard(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(conversationService.undoCard(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("undoCard failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 重置当前会话上下文。
     *
     * @return 操作成功返回 {@code {"ok": true}}，否则返回 500。
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, Boolean>> reset() {
        try {
            conversationService.reset();
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            log.error("reset failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
