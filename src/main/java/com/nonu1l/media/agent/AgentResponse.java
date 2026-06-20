package com.nonu1l.media.agent;

/**
 * 单轮 Agent 最终响应，content 用于直接展示，contentBlocks 保存 Anthropic-like 内容块。
 *
 * @param content 最终展示文本
 * @param contentBlocks 内容块 JSON
 */
public record AgentResponse(String content, String contentBlocks) {
}
