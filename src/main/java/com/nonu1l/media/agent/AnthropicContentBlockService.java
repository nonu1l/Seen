package com.nonu1l.media.agent;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将 Spring AI Anthropic 响应转换为前端可展示和调试的 Anthropic-like content blocks。
 */
@Service
public class AnthropicContentBlockService {

    private final ObjectMapper objectMapper;

    /**
     * @param objectMapper JSON 序列化工具，用于持久化 content blocks。
     */
    public AnthropicContentBlockService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 从 ChatResponse 提取最终文本。
     *
     * @param response Spring AI 聊天响应
     * @return 合并后的文本，可能为空
     */
    public String textFrom(ChatResponse response) {
        if (response == null || response.getResults() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Generation generation : response.getResults()) {
            AssistantMessage message = generation.getOutput();
            String text = message != null ? message.getText() : null;
            if (text != null && !text.isBlank()) {
                if (!sb.isEmpty()) {
                    sb.append("\n");
                }
                sb.append(text.trim());
            }
        }
        return sb.toString();
    }

    /**
     * 将响应转换为 JSON 内容块；至少会保留 text block。
     *
     * @param response Spring AI 聊天响应
     * @param fallbackText 未能提取 blocks 时使用的文本
     * @return JSON 数组字符串
     */
    public String blocksJson(ChatResponse response, String fallbackText) {
        List<Map<String, Object>> blocks = new ArrayList<>();
        if (response != null && response.getResults() != null) {
            for (Generation generation : response.getResults()) {
                AssistantMessage message = generation.getOutput();
                if (message == null) {
                    continue;
                }
                appendThinkingBlocks(blocks, message.getMetadata());
                String text = message.getText();
                if (text != null && !text.isBlank()) {
                    blocks.add(Map.of("type", "text", "text", text));
                }
                if (message.hasToolCalls()) {
                    message.getToolCalls().forEach(toolCall -> {
                        Map<String, Object> block = new LinkedHashMap<>();
                        block.put("type", "tool_use");
                        block.put("id", toolCall.id());
                        block.put("name", toolCall.name());
                        block.put("input", toolCall.arguments());
                        blocks.add(block);
                    });
                }
            }
        }
        if (blocks.isEmpty() && fallbackText != null && !fallbackText.isBlank()) {
            blocks.add(Map.of("type", "text", "text", fallbackText));
        }
        try {
            return objectMapper.writeValueAsString(blocks);
        } catch (Exception ignored) {
            return "[]";
        }
    }

    private void appendThinkingBlocks(List<Map<String, Object>> blocks, Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return;
        }
        Object thinking = metadata.get("thinking");
        if (thinking != null && !String.valueOf(thinking).isBlank()) {
            Map<String, Object> block = new LinkedHashMap<>();
            block.put("type", "thinking");
            block.put("thinking", String.valueOf(thinking));
            if (metadata.containsKey("signature")) {
                block.put("signature", metadata.get("signature"));
            }
            blocks.add(block);
        }
    }
}
