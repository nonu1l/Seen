package com.nonu1l.media.agent;

import com.nonu1l.media.agent.tool.AiToolRegistry;
import com.nonu1l.media.config.TokenUsageAdvisor;
import com.nonu1l.media.service.AiChatClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * 自主 Agent 服务：由模型自主选择工具执行搜索、推荐、分析、标记和取消标记。
 */
@Service
public class AutonomousAgentService {

    private static final Logger log = LoggerFactory.getLogger(AutonomousAgentService.class);

    private final AiChatClientFactory chatClientFactory;
    private final AiToolRegistry toolRegistry;
    private final String prompt;

    /**
     * 创建自主 Agent 服务。
     *
     * @param chatClientFactory AI 客户端工厂
     * @param toolRegistry 工具注册器
     */
    public AutonomousAgentService(AiChatClientFactory chatClientFactory, AiToolRegistry toolRegistry) {
        this.chatClientFactory = chatClientFactory;
        this.toolRegistry = toolRegistry;
        this.prompt = loadPrompt("prompts/agent-autonomous.st");
    }

    /**
     * 执行一轮自主 Agent 对话。
     *
     * @param userInput 用户输入
     * @param history 最近会话历史
     * @param listener 运行状态监听器
     * @return 最终助手回复文本
     */
    public String invoke(String userInput, String history, AgentRunListener listener) {
        AgentRunListener runListener = listener != null ? listener : AgentRunEvents.noop();
        runListener.status("正在理解需求");
        TokenUsageAdvisor.setCurrentNode("autonomous-agent");
        String system = prompt
                .replace("{today}", LocalDate.now().toString())
                .replace("{history}", history != null ? history : "")
                .replace("{webToolGuidance}", toolRegistry.webToolGuidance())
                .replace("{analysisToolList}", toolRegistry.analysisToolList())
                .replace("{webFailureGuidance}", toolRegistry.webFailureGuidance());
        String content = chatClient().prompt()
                .system(system)
                .user(userInput)
                .toolCallbacks(toolRegistry.callbacks())
                .call()
                .content();
        String cleaned = cleanAssistantContent(content);
        if (cleaned == null || cleaned.isBlank()) {
            return "已处理。";
        }
        log.debug("Autonomous agent reply: {}", cleaned.length() > 200 ? cleaned.substring(0, 200) : cleaned);
        return cleaned;
    }

    private ChatClient chatClient() {
        return chatClientFactory.currentClient();
    }

    private String cleanAssistantContent(String content) {
        return chatClientFactory.cleanAssistantContent(content);
    }

    private static String loadPrompt(String path) {
        try {
            return new String(new ClassPathResource(path).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load prompt: " + path, e);
        }
    }
}
