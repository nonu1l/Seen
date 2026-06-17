package com.nonu1l.media.agent;

/**
 * Agent 单轮运行监听器，用于把内部流程状态和最终回复增量转发给流式接口。
 */
public interface AgentRunListener {

    /**
     * 推送一条用户可见的简洁流程状态。
     *
     * @param message 状态文案
     */
    void status(String message);

    /**
     * 推送助手回复文本增量。
     *
     * @param content 增量文本
     */
    void delta(String content);

    /**
     * 推送用户可见错误。
     *
     * @param message 错误文案
     */
    void error(String message);

    /**
     * 当前监听器是否希望接收真实流式文本增量。
     *
     * @return 支持增量展示时返回 true
     */
    default boolean streamDeltas() {
        return false;
    }

    /**
     * 当前运行中是否已经发送过文本增量。
     *
     * @return 已发送过 delta 时返回 true
     */
    default boolean hasDelta() {
        return false;
    }
}
