package com.nonu1l.media.controller;

import com.nonu1l.media.model.dto.*;
import com.nonu1l.media.service.ConversationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/conversation")
public class ConversationController {

    private static final Logger log = LoggerFactory.getLogger(ConversationController.class);
    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping("/state")
    public ResponseEntity<ConversationState> getState() {
        try {
            return ResponseEntity.ok(conversationService.getState());
        } catch (Exception e) {
            log.error("getState failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/send")
    public ResponseEntity<AiChatResponse> send(@RequestBody AiChatRequest req) {
        try {
            if (req.userInput() == null || req.userInput().isBlank()) {
                return ResponseEntity.badRequest().build();
            }
            return ResponseEntity.ok(conversationService.sendMessage(req.userInput().trim()));
        } catch (Exception e) {
            log.error("send failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/cards/{id}/save")
    public ResponseEntity<ConversationCardVO> saveCard(@PathVariable Long id,
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

    @PostMapping("/cards/{id}/undo")
    public ResponseEntity<ConversationCardVO> undoCard(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(conversationService.undoCard(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("undoCard failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

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
