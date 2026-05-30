package com.nonu1l.media.config;

import com.nonu1l.media.model.entity.TokenUsage;
import com.nonu1l.media.repository.TokenUsageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;

/**
 * Spring AI CallAdvisor — 拦截 LLM 调用，记录 token 用量到 token_usage 表。
 */
public class TokenUsageAdvisor implements CallAdvisor {

    private static final Logger log = LoggerFactory.getLogger(TokenUsageAdvisor.class);

    private final TokenUsageRepository repo;

    public TokenUsageAdvisor(TokenUsageRepository repo) {
        this.repo = repo;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        // 截取输入文本（截断防止过长）
        String inputText = truncate(request.prompt().getContents(), 8000);

        ChatClientResponse response = chain.nextCall(request);

        var chatResponse = response.chatResponse();
        if (chatResponse != null && chatResponse.getMetadata() != null) {
            Usage usage = chatResponse.getMetadata().getUsage();
            if (usage != null) {
                String model = chatResponse.getMetadata().getModel();
                String outputText = truncate(
                        chatResponse.getResult().getOutput().getText(), 8000);
                try {
                    TokenUsage tu = new TokenUsage();
                    tu.setModelName(model != null ? model : "unknown");
                    tu.setPromptTokens(usage.getPromptTokens() > 0 ? (int) usage.getPromptTokens() : null);
                    tu.setCompletionTokens(usage.getCompletionTokens() > 0 ? (int) usage.getCompletionTokens() : null);
                    tu.setTotalTokens(usage.getTotalTokens() > 0 ? (int) usage.getTotalTokens() : null);
                    tu.setInputText(inputText);
                    tu.setOutputText(outputText);
                    repo.save(tu);
                    log.debug("Token: model={} prompt={} completion={} total={}",
                            tu.getModelName(), tu.getPromptTokens(), tu.getCompletionTokens(), tu.getTotalTokens());
                } catch (Exception e) {
                    log.warn("Failed to persist token usage: {}", e.getMessage());
                }
            }
        }
        return response;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public String getName() {
        return "token-usage-advisor";
    }
}
