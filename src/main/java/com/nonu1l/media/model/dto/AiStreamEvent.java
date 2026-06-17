package com.nonu1l.media.model.dto;

import java.time.Instant;
import java.util.List;

/**
 * AI 流式对话事件，供 SSE 接口按步骤推送消息、状态、增量文本和卡片。
 *
 * @param type 事件类型
 * @param messageId 关联消息 ID；user_saved 为用户消息，assistant_saved 为助手消息
 * @param content 状态文案、增量文本或最终回复正文
 * @param createdAt 消息创建时间
 * @param cards 本轮生成的卡片列表
 */
public record AiStreamEvent(
        String type,
        Long messageId,
        String content,
        Instant createdAt,
        List<ConversationCardVO> cards
) {

    /**
     * 创建用户消息已保存事件。
     *
     * @param messageId 用户消息 ID
     * @param createdAt 创建时间
     * @return SSE 事件
     */
    public static AiStreamEvent userSaved(Long messageId, Instant createdAt) {
        return new AiStreamEvent("user_saved", messageId, null, createdAt, null);
    }

    /**
     * 创建流程状态事件。
     *
     * @param content 状态文案
     * @return SSE 事件
     */
    public static AiStreamEvent status(String content) {
        return new AiStreamEvent("status", null, content, null, null);
    }

    /**
     * 创建助手文本增量事件。
     *
     * @param content 增量文本
     * @return SSE 事件
     */
    public static AiStreamEvent delta(String content) {
        return new AiStreamEvent("delta", null, content, null, null);
    }

    /**
     * 创建助手消息已保存事件。
     *
     * @param messageId 助手消息 ID
     * @param content 完整回复正文
     * @param createdAt 创建时间
     * @return SSE 事件
     */
    public static AiStreamEvent assistantSaved(Long messageId, String content, Instant createdAt) {
        return new AiStreamEvent("assistant_saved", messageId, content, createdAt, null);
    }

    /**
     * 创建本轮卡片事件。
     *
     * @param cards 卡片列表
     * @return SSE 事件
     */
    public static AiStreamEvent cards(List<ConversationCardVO> cards) {
        return new AiStreamEvent("cards", null, null, null, cards);
    }

    /**
     * 创建完成事件。
     *
     * @return SSE 事件
     */
    public static AiStreamEvent done() {
        return new AiStreamEvent("done", null, null, null, null);
    }

    /**
     * 创建错误事件。
     *
     * @param content 用户可见错误文案
     * @return SSE 事件
     */
    public static AiStreamEvent error(String content) {
        return new AiStreamEvent("error", null, content, null, null);
    }
}
