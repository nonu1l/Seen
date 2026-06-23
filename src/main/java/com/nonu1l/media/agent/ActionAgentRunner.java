package com.nonu1l.media.agent;

import com.nonu1l.media.agent.tool.AgentToolRegistry;
import com.nonu1l.media.agent.tool.AiAutonomousTools;
import com.nonu1l.media.agent.tool.AiBangumiTools;
import com.nonu1l.media.agent.tool.AiLocalLibraryTools;
import com.nonu1l.media.service.AiChatCallService;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 写库操作 Runner：只允许查候选、查本地状态、标记和取消标记。
 */
@Component
public class ActionAgentRunner extends AbstractAgentRunner {

    private final AgentToolRegistry toolRegistry;
    private final AiBangumiTools bangumiTools;
    private final AiLocalLibraryTools localLibraryTools;
    private final AiAutonomousTools autonomousTools;

    /**
     * @param aiChatCallService LLM 调用服务
     * @param contentBlockService 内容块转换服务
     * @param toolRegistry 工具注册器
     * @param bangumiTools Bangumi 搜索工具
     * @param localLibraryTools 本地片库工具
     * @param autonomousTools 包含 markWork/unmarkWork/getWorkState 的工具 Bean
     */
    public ActionAgentRunner(AiChatCallService aiChatCallService,
                             AnthropicContentBlockService contentBlockService,
                             AgentToolRegistry toolRegistry,
                             AiBangumiTools bangumiTools,
                             AiLocalLibraryTools localLibraryTools,
                             AiAutonomousTools autonomousTools) {
        super(aiChatCallService, contentBlockService, "prompts/agent-action.st");
        this.toolRegistry = toolRegistry;
        this.bangumiTools = bangumiTools;
        this.localLibraryTools = localLibraryTools;
        this.autonomousTools = autonomousTools;
    }

    @Override
    public AgentCapability capability() {
        return AgentCapability.ACTION;
    }

    @Override
    public AgentResponse run(String userInput, String history, AgentRunListener listener) {
        if (listener != null) {
            listener.status("正在处理标记操作");
        }
        Object[] tools = toolRegistry.selectLimited("agent-action", 5,
                Set.of("searchBangumi", "searchLocal", "getWorkState", "markWork", "unmarkWork"),
                bangumiTools, localLibraryTools, autonomousTools);
        return callAgent("agent-action", userInput, history, null, tools);
    }
}
