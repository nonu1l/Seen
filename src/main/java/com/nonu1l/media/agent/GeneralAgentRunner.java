package com.nonu1l.media.agent;

import com.nonu1l.media.service.AiChatCallService;
import org.springframework.stereotype.Component;

/**
 * 普通对话 Runner：不暴露任何工具，避免闲聊触发业务副作用。
 */
@Component
public class GeneralAgentRunner extends AbstractAgentRunner {

    /**
     * @param aiChatCallService LLM 调用服务
     * @param contentBlockService 内容块转换服务
     */
    public GeneralAgentRunner(AiChatCallService aiChatCallService,
                              AnthropicContentBlockService contentBlockService) {
        super(aiChatCallService, contentBlockService, "prompts/agent-general.st");
    }

    @Override
    public AgentCapability capability() {
        return AgentCapability.GENERAL;
    }

    @Override
    public AgentResponse run(String userInput, String history, AgentRunListener listener) {
        if (listener != null) {
            listener.status("正在回复");
        }
        return callAgent("agent-general", userInput, history, null, new Object[0]);
    }
}
