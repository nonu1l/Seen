package com.nonu1l.media.agent;

import com.nonu1l.media.agent.tool.AgentToolRegistry;
import com.nonu1l.media.agent.tool.AiWatchSourceTools;
import com.nonu1l.media.service.AiChatCallService;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 片源 Runner：只允许调用 searchWatchSources，避免回落到通用 Web 搜索后编造平台。
 */
@Component
public class WatchSourceAgentRunner extends AbstractAgentRunner {

    private final AgentToolRegistry toolRegistry;
    private final AiWatchSourceTools watchSourceTools;

    /**
     * @param aiChatCallService LLM 调用服务
     * @param contentBlockService 内容块转换服务
     * @param toolRegistry 工具注册器
     * @param watchSourceTools 片源搜索工具
     */
    public WatchSourceAgentRunner(AiChatCallService aiChatCallService,
                                  AnthropicContentBlockService contentBlockService,
                                  AgentToolRegistry toolRegistry,
                                  AiWatchSourceTools watchSourceTools) {
        super(aiChatCallService, contentBlockService, "prompts/agent-watch-source.st");
        this.toolRegistry = toolRegistry;
        this.watchSourceTools = watchSourceTools;
    }

    @Override
    public AgentCapability capability() {
        return AgentCapability.WATCH_SOURCE;
    }

    @Override
    public AgentResponse run(String userInput, String history, AgentRunListener listener) {
        if (listener != null) {
            listener.status("正在搜索观看地址");
        }
        Object[] tools = toolRegistry.selectLimited("agent-watch-source", 1,
                Set.of("searchWatchSources"), watchSourceTools);
        return callAgent("agent-watch-source", userInput, history, null, tools);
    }
}
