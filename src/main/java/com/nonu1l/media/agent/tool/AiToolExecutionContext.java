package com.nonu1l.media.agent.tool;

import com.nonu1l.media.agent.AgentRunListener;

/**
 * 单轮自主 Agent 工具执行上下文，用于把工具副作用归属到会话请求和消息。
 *
 * @param sessionId 会话 ID
 * @param requestId 本轮请求 ID
 * @param userMessageId 用户消息 ID
 * @param assistantMessageId 助手消息 ID
 * @param userInput 当前用户原始输入，用于工具安全策略判断高风险语义
 * @param listener 运行状态监听器
 */
public record AiToolExecutionContext(
        Long sessionId,
        String requestId,
        Long userMessageId,
        Long assistantMessageId,
        String userInput,
        AgentRunListener listener
) {
}
