package com.nonu1l.media.agent;

import com.nonu1l.media.service.AiChatCallService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 受控 Runner 公共调用逻辑：准备基础模板参数、调用 LLM，并转换 Anthropic-like content blocks。
 */
abstract class AbstractAgentRunner implements AgentRunner {

    private static final Logger log = LoggerFactory.getLogger(AbstractAgentRunner.class);

    private final AiChatCallService aiChatCallService;
    private final AnthropicContentBlockService contentBlockService;
    private final String prompt;

    /**
     * @param aiChatCallService LLM 调用服务
     * @param contentBlockService 内容块转换服务
     * @param promptPath prompt classpath 路径
     */
    protected AbstractAgentRunner(AiChatCallService aiChatCallService,
                                  AnthropicContentBlockService contentBlockService,
                                  String promptPath) {
        this.aiChatCallService = aiChatCallService;
        this.contentBlockService = contentBlockService;
        this.prompt = AgentPromptLoader.load(promptPath);
    }

    /**
     * 执行一次受控工具 Agent 调用。
     *
     * @param node token usage 节点名
     * @param userInput 用户输入
     * @param history 会话历史
     * @param extraParams prompt 额外参数
     * @param tools 当前 Runner 暴露的工具回调
     * @return 标准 Agent 响应
     */
    protected AgentResponse callAgent(String node, String userInput, String history,
                                      Map<String, Object> extraParams, Object[] tools) {
        Map<String, Object> params = baseParams(history);
        if (extraParams != null && !extraParams.isEmpty()) {
            params.putAll(extraParams);
        }
        ChatResponse response = aiChatCallService.agent()
                .node(node)
                .system(prompt, params)
                .user(userInput)
                .tools(tools)
                .callOnceResponse();
        String replyText = contentBlockService.textFrom(response);
        if (replyText == null || replyText.isBlank()) {
            replyText = "已处理。";
        }
        log.debug("{} reply: {}", node, replyText.length() > 200 ? replyText.substring(0, 200) : replyText);
        return new AgentResponse(replyText, contentBlockService.blocksJson(response, replyText));
    }

    protected Map<String, Object> baseParams(String history) {
        Map<String, Object> params = new HashMap<>();
        params.put("today", LocalDate.now().toString());
        params.put("history", history != null ? history : "");
        return params;
    }

    /**
     * @return 当前 Runner 复用的 LLM 调用服务，供需要自定义调用流程的子类使用。
     */
    protected AiChatCallService aiChatCallService() {
        return aiChatCallService;
    }

    /**
     * @return 当前 Runner 复用的内容块转换服务，供需要自定义响应组装的子类使用。
     */
    protected AnthropicContentBlockService contentBlockService() {
        return contentBlockService;
    }
}
