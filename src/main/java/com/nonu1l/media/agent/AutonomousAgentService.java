package com.nonu1l.media.agent;

import com.nonu1l.media.agent.tool.AiAutonomousTools;
import com.nonu1l.media.agent.tool.AiBangumiTools;
import com.nonu1l.media.agent.tool.AiLocalLibraryTools;
import com.nonu1l.media.agent.tool.AiWatchSourceTools;
import com.nonu1l.media.agent.tool.AiWebSearchTools;
import com.nonu1l.media.agent.tool.AgentToolRegistry;
import com.nonu1l.media.service.AiChatCallService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

/**
 * 自主 Agent 服务：由模型自主选择工具执行搜索、推荐、分析、标记和取消标记。
 */
@Service
public class AutonomousAgentService {

    private static final Logger log = LoggerFactory.getLogger(AutonomousAgentService.class);

    private final AiChatCallService aiChatCallService;
    private final AgentToolRegistry toolRegistry;
    private final AnthropicContentBlockService contentBlockService;
    private final AiBangumiTools bangumiTools;
    private final AiLocalLibraryTools localLibraryTools;
    private final AiAutonomousTools autonomousTools;
    private final AiWatchSourceTools watchSourceTools;
    private final AiWebSearchTools webSearchTools;
    private final String prompt;

    /**
     * 创建自主 Agent 服务。
     *
     * @param aiChatCallService LLM 调用服务
     * @param toolRegistry 轻量工具注册器
     * @param contentBlockService Anthropic 内容块解析服务
     * @param bangumiTools Bangumi 搜索工具
     * @param localLibraryTools 本地片库工具
     * @param autonomousTools 自主 Agent 操作工具
     * @param watchSourceTools 片源搜索工具
     * @param webSearchTools Web 搜索与抓取工具
     */
    public AutonomousAgentService(AiChatCallService aiChatCallService,
                                  AgentToolRegistry toolRegistry,
                                  AnthropicContentBlockService contentBlockService,
                                  AiBangumiTools bangumiTools,
                                  AiLocalLibraryTools localLibraryTools,
                                  AiAutonomousTools autonomousTools,
                                  AiWatchSourceTools watchSourceTools,
                                  AiWebSearchTools webSearchTools) {
        this.aiChatCallService = aiChatCallService;
        this.toolRegistry = toolRegistry;
        this.contentBlockService = contentBlockService;
        this.bangumiTools = bangumiTools;
        this.localLibraryTools = localLibraryTools;
        this.autonomousTools = autonomousTools;
        this.watchSourceTools = watchSourceTools;
        this.webSearchTools = webSearchTools;
        this.prompt = loadPrompt("prompts/agent-autonomous.st");
    }

    /**
     * 执行一轮自主 Agent 对话。
     *
     * @param userInput 用户输入
     * @param history 最近会话历史
     * @param listener 运行状态监听器
     * @return 最终助手回复和 content blocks
     */
    public AgentResponse invoke(String userInput, String history, AgentRunListener listener) {
        AgentRunListener runListener = listener != null ? listener : AgentRunEvents.noop();
        runListener.status("正在理解需求");
        Map<String, Object> systemParams = Map.of(
                "today", LocalDate.now().toString(),
                "history", history != null ? history : "",
//                "webToolGuidance", toolRegistry.webToolGuidance(),
//                "analysisToolList", toolRegistry.analysisToolList(),
                "webFailureGuidance", toolRegistry.webFailureGuidance()
        );
        ChatResponse response = aiChatCallService.agent()
                .node("autonomous-agent")
                .system(prompt, systemParams)
                .user(userInput)
                .tools(toolRegistry.select(
                        "autonomous-agent",
                        bangumiTools,
                        localLibraryTools,
                        autonomousTools,
                        watchSourceTools,
                        webSearchTools
                ))
                .callOnceResponse();
        String replyText = contentBlockService.textFrom(response);
        if (replyText == null || replyText.isBlank()) {
            replyText = "已处理。";
        }
        log.debug("Autonomous agent reply: {}", replyText.length() > 200 ? replyText.substring(0, 200) : replyText);
        return new AgentResponse(replyText, contentBlockService.blocksJson(response, replyText));
    }

    private static String loadPrompt(String path) {
        try {
            return new String(new ClassPathResource(path).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load prompt: " + path, e);
        }
    }

}
