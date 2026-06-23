package com.nonu1l.media.agent;

/**
 * 受控 Agent 子流程 Runner，只暴露当前能力需要的工具。
 */
public interface AgentRunner {

    /**
     * @return 当前 Runner 负责的能力类型。
     */
    AgentCapability capability();

    /**
     * 执行一轮受控 Agent 子流程。
     *
     * @param userInput 用户原始输入
     * @param history 最近会话历史
     * @param listener 运行状态监听器
     * @return 最终助手回复
     */
    AgentResponse run(String userInput, String history, AgentRunListener listener);
}
