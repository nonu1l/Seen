package com.nonu1l.media.model.dto;

import java.time.Instant;
import java.util.List;

/**
 * 当前正在执行的 AI 对话轮次快照，用于页面关闭后重新进入时恢复流式过程。
 *
 * @param active 是否存在仍在处理中的轮次
 * @param userMessageId 本轮用户消息 ID
 * @param assistantMessageId 本轮助手消息 ID；未落库时为空
 * @param assistantContent 已生成的助手回复片段
 * @param statuses 最近的流程状态文案
 * @param startedAt 本轮开始时间
 * @param updatedAt 最近更新时间
 * @param error 最近的错误文案
 */
public record ConversationRunState(
        boolean active,
        Long userMessageId,
        Long assistantMessageId,
        String assistantContent,
        List<String> statuses,
        Instant startedAt,
        Instant updatedAt,
        String error
) {

    /**
     * 创建无活动轮次的空快照。
     *
     * @return inactive 状态快照
     */
    public static ConversationRunState inactive() {
        return new ConversationRunState(false, null, null, "", List.of(), null, null, null);
    }
}
