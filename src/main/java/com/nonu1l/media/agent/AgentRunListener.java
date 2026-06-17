package com.nonu1l.media.agent;

/**
 * Agent 单轮运行监听器，用于把内部流程状态转发给流式接口。
 */
public interface AgentRunListener {

    /**
     * 推送一条用户可见的简洁流程状态。
     *
     * @param message 状态文案
     */
    void status(String message);

    /**
     * 推送用户可见错误。
     *
     * @param message 错误文案
     */
    void error(String message);
}
