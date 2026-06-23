package com.nonu1l.media.agent;

import com.nonu1l.media.agent.tool.AgentToolRegistry;
import com.nonu1l.media.agent.tool.AiAutonomousTools;
import com.nonu1l.media.service.AiChatCallService;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 推荐/搜索 Runner：通过原子工具生成 PENDING 卡片，不暴露写库工具。
 */
@Component
public class FindWorksAgentRunner extends AbstractAgentRunner {

    private final AgentToolRegistry toolRegistry;
    private final AiAutonomousTools autonomousTools;

    /**
     * @param aiChatCallService LLM 调用服务
     * @param contentBlockService 内容块转换服务
     * @param toolRegistry 工具注册器
     * @param autonomousTools 包含 findAndPresentWorks/readUserMemory 的工具 Bean
     */
    public FindWorksAgentRunner(AiChatCallService aiChatCallService,
                                AnthropicContentBlockService contentBlockService,
                                AgentToolRegistry toolRegistry,
                                AiAutonomousTools autonomousTools) {
        super(aiChatCallService, contentBlockService, "prompts/agent-find-works.st");
        this.toolRegistry = toolRegistry;
        this.autonomousTools = autonomousTools;
    }

    @Override
    public AgentCapability capability() {
        return AgentCapability.FIND_WORKS;
    }

    @Override
    public AgentResponse run(String userInput, String history, AgentRunListener listener) {
        if (listener != null) {
            listener.status("正在查找候选作品");
        }
        Object[] tools = toolRegistry.selectLimited("agent-find-works", 2,
                Set.of("findAndPresentWorks", "readUserMemory"), autonomousTools);
        return callAgent("agent-find-works", userInput, history, null, tools);
    }
}
