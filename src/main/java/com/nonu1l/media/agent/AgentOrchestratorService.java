package com.nonu1l.media.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 主入口：先进行轻量能力路由，再交给对应受控 Runner 执行。
 */
@Service
public class AgentOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestratorService.class);

    private final AgentCapabilityRouter router;
    private final Map<AgentCapability, AgentRunner> runners;

    /**
     * @param router 能力路由器
     * @param runnerList 所有受控 Runner
     */
    public AgentOrchestratorService(AgentCapabilityRouter router, List<AgentRunner> runnerList) {
        this.router = router;
        this.runners = new EnumMap<>(AgentCapability.class);
        for (AgentRunner runner : runnerList) {
            this.runners.put(runner.capability(), runner);
        }
    }

    /**
     * 执行一轮 Agent 对话。
     *
     * @param userInput 用户输入
     * @param history 最近会话历史
     * @param listener 运行状态监听器
     * @return 最终助手回复和 content blocks
     */
    public AgentResponse invoke(String userInput, String history, AgentRunListener listener) {
        AgentRunListener runListener = listener != null ? listener : AgentRunEvents.noop();
        runListener.status("正在理解需求");
        AgentCapability capability = router.route(userInput, history);
        AgentRunner runner = runners.getOrDefault(capability, runners.get(AgentCapability.ANALYSIS));
        if (runner == null) {
            throw new IllegalStateException("No agent runner for capability: " + capability);
        }
        log.debug("Agent routed to {}", capability);
        return runner.run(userInput, history, runListener);
    }
}
