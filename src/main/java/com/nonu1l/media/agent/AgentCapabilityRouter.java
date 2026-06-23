package com.nonu1l.media.agent;

import com.nonu1l.media.service.AiChatCallService;
import com.nonu1l.media.service.AiThinkingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;

/**
 * LLM 能力路由：结合当前输入和最近历史输出能力枚举，不在代码中堆叠关键词规则。
 */
@Service
public class AgentCapabilityRouter {

    private static final Logger log = LoggerFactory.getLogger(AgentCapabilityRouter.class);

    private final AiChatCallService aiChatCallService;
    private final String prompt;

    /**
     * @param aiChatCallService LLM 调用服务，用于能力路由判断
     */
    public AgentCapabilityRouter(AiChatCallService aiChatCallService) {
        this.aiChatCallService = aiChatCallService;
        this.prompt = AgentPromptLoader.load("prompts/agent-router.st");
    }

    /**
     * @param userInput 用户原始输入
     * @param history 最近会话历史
     * @return 命中的能力类型
     */
    public AgentCapability route(String userInput, String history) {
        String text = userInput != null ? userInput.trim() : "";
        if (text.isBlank()) {
            return AgentCapability.GENERAL;
        }
        try {
            String answer = aiChatCallService.task()
                    .node("agent-router")
                    .system(prompt, Map.of("history", history != null ? history : ""))
                    .user(text)
                    .thinking(AiThinkingMode.DISABLED)
                    .maxAttempts(1)
                    .call();
            AgentCapability routed = parse(answer);
            log.debug("Agent routed by LLM: input='{}', answer='{}', capability={}", text, answer, routed);
            return routed;
        } catch (RuntimeException e) {
            log.warn("Agent router fallback to ANALYSIS: {}", e.getMessage());
            return AgentCapability.ANALYSIS;
        }
    }

    private AgentCapability parse(String answer) {
        String normalized = answer != null ? answer.toUpperCase(Locale.ROOT) : "";
        for (AgentCapability capability : AgentCapability.values()) {
            if (normalized.contains(capability.name())) {
                return capability;
            }
        }
        return AgentCapability.ANALYSIS;
    }
}
