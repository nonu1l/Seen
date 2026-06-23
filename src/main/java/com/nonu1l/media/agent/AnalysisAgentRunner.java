package com.nonu1l.media.agent;

import com.nonu1l.media.agent.tool.AgentToolRegistry;
import com.nonu1l.media.agent.tool.AiAutonomousTools;
import com.nonu1l.media.agent.tool.AiLocalLibraryTools;
import com.nonu1l.media.agent.tool.AiWebSearchTools;
import com.nonu1l.media.service.AiChatCallService;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 分析/问答 Runner：只暴露只读工具；搜索源关闭时不回答实时事实。
 */
@Component
public class AnalysisAgentRunner extends AbstractAgentRunner {

    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");
    private static final Pattern REALTIME_PATTERN = Pattern.compile(
            "(最新|最近|当前|现在|今日|今天|今年|榜单|排行|票房|热映|上映|实时)");

    private final AgentToolRegistry toolRegistry;
    private final AiLocalLibraryTools localLibraryTools;
    private final AiAutonomousTools autonomousTools;
    private final AiWebSearchTools webSearchTools;

    /**
     * @param aiChatCallService LLM 调用服务
     * @param contentBlockService 内容块转换服务
     * @param toolRegistry 工具注册器
     * @param localLibraryTools 本地片库只读工具
     * @param autonomousTools 包含 getWorkState/readUserMemory 的工具 Bean
     * @param webSearchTools Web 搜索与抓取工具
     */
    public AnalysisAgentRunner(AiChatCallService aiChatCallService,
                               AnthropicContentBlockService contentBlockService,
                               AgentToolRegistry toolRegistry,
                               AiLocalLibraryTools localLibraryTools,
                               AiAutonomousTools autonomousTools,
                               AiWebSearchTools webSearchTools) {
        super(aiChatCallService, contentBlockService, "prompts/agent-analysis.st");
        this.toolRegistry = toolRegistry;
        this.localLibraryTools = localLibraryTools;
        this.autonomousTools = autonomousTools;
        this.webSearchTools = webSearchTools;
    }

    @Override
    public AgentCapability capability() {
        return AgentCapability.ANALYSIS;
    }

    @Override
    public AgentResponse run(String userInput, String history, AgentRunListener listener) {
        if (listener != null) {
            listener.status("正在分析");
        }
        boolean webEnabled = toolRegistry.isWebSearchEnabled();
        boolean hasUrl = userInput != null && URL_PATTERN.matcher(userInput).find();
        Set<String> allowedTools = new HashSet<>(Set.of("searchLocal", "getWorkState", "readUserMemory"));
        if (webEnabled) {
            allowedTools.add("searchWeb");
        }
        if (hasUrl) {
            allowedTools.add("fetchWeb");
        }
        Object[] tools = toolRegistry.selectLimited("agent-analysis", 3, allowedTools,
                localLibraryTools, autonomousTools, webSearchTools);
        Map<String, Object> params = Map.of(
                "realtimeGuidance", realtimeGuidance(userInput, webEnabled, hasUrl)
        );
        return callAgent("agent-analysis", userInput, history, params, tools);
    }

    private String realtimeGuidance(String userInput, boolean webEnabled, boolean hasUrl) {
        boolean realtime = userInput != null && REALTIME_PATTERN.matcher(userInput).find();
        if (!webEnabled && realtime && !hasUrl) {
            return "当前未启用搜索源，用户询问实时、最新或榜单类问题时，必须说明无法实时查询，不要凭旧知识回答。";
        }
        if (webEnabled) {
            return "搜索源已启用；实时、最新或榜单类问题可调用 searchWeb，失败后如实说明资料源不可用。";
        }
        return "当前未启用搜索源；只有用户提供明确 URL 时才可调用 fetchWeb 读取公开页面。";
    }
}
