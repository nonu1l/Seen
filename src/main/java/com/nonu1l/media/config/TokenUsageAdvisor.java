package com.nonu1l.media.config;

import com.nonu1l.media.model.entity.TokenUsage;
import com.nonu1l.media.repository.TokenUsageRepository;
import com.nonu1l.media.service.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;

import java.util.function.Supplier;

/**
 * Spring AI 调用拦截器：记录每次 LLM 调用产生的 token 用量到数据库，并提供会话级上下文注入能力。
 */
public class TokenUsageAdvisor implements CallAdvisor {

    private static final Logger log = LoggerFactory.getLogger(TokenUsageAdvisor.class);
    private static final ThreadLocal<Long> currentSession = new ThreadLocal<>();
    private static final ThreadLocal<String> currentNode = new ThreadLocal<>();
    private static final ThreadLocal<Integer> currentTurn = new ThreadLocal<>();

    private final TokenUsageRepository repo;
    private final SettingsService settingsService;
    private final Supplier<SettingsService.AiRuntimeSetting> settingSupplier;

    /**
     * 通过仓储初始化 advisor。
     *
     * @param repo token 用量仓储。
     * @param settingsService 设置读取服务。
     */
    public TokenUsageAdvisor(TokenUsageRepository repo,
                             SettingsService settingsService) {
        this.repo = repo;
        this.settingsService = settingsService;
        this.settingSupplier = settingsService::currentRuntimeSetting;
    }

    private TokenUsageAdvisor(TokenUsageRepository repo,
                              SettingsService settingsService,
                              Supplier<SettingsService.AiRuntimeSetting> settingSupplier) {
        this.repo = repo;
        this.settingsService = settingsService;
        this.settingSupplier = settingSupplier;
    }

    /**
     * 为动态创建的 ChatClient 绑定其实际使用的 AI 配置，避免运行中保存配置时记账串号。
     *
     * @param setting ChatClient 构建时使用的运行时配置。
     * @return 绑定指定配置的 Advisor。
     */
    public TokenUsageAdvisor withSetting(SettingsService.AiRuntimeSetting setting) {
        return new TokenUsageAdvisor(repo, settingsService, () -> setting);
    }

    /**
     * 设置当前线程会话上下文，用于后续记录时归属到同一次会话。
     *
     * @param sessionId 会话 ID。
     */
    public static void setSession(Long sessionId) {
        currentSession.set(sessionId);
    }

    /**
     * 清空当前线程会话上下文，避免污染下一个会话线程。
     */
    public static void clearSession() {
        currentSession.remove();
    }

    /**
     * 设置当前调用所属节点。
     *
     * @param node 节点名称（如具体 agent/步骤名）。
     */
    public static void setCurrentNode(String node) {
        currentNode.set(node);
    }

    /**
     * 设置当前调用轮次。
     *
     * @param turn 轮次编号。
     */
    public static void setCurrentTurn(Integer turn) {
        currentTurn.set(turn);
    }

    /**
     * 在链路调用前后收集 metadata 并持久化 token 统计。
     *
     * @param request 原始聊天请求。
     * @param chain   下游调用链。
     * @return LLM 响应原文。
     */
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        String inputText = request.prompt().getContents();

        ChatClientResponse response = chain.nextCall(request);
        if (!settingsService.getBoolean(SettingsService.AI_TOKEN_USAGE_ENABLED)) {
            return response;
        }

        var chatResponse = response.chatResponse();
        if (chatResponse != null && chatResponse.getMetadata() != null) {
            Usage usage = chatResponse.getMetadata().getUsage();
            if (usage != null) {
                String model = chatResponse.getMetadata().getModel();
                String outputText = chatResponse.getResult().getOutput().getText();
                try {
                    SettingsService.AiRuntimeSetting setting = settingSupplier.get();
                    TokenUsage tu = new TokenUsage();
                    tu.setSessionId(currentSession.get());
                    tu.setNodeName(currentNode.get());
                    tu.setTurn(currentTurn.get());
                    tu.setProfileId(setting.id());
                    tu.setProfileName(setting.providerKind());
                    tu.setModelName(model != null ? model : "unknown");
                    tu.setPromptTokens(usage.getPromptTokens() > 0 ? (int) usage.getPromptTokens() : null);
                    tu.setCompletionTokens(usage.getCompletionTokens() > 0 ? (int) usage.getCompletionTokens() : null);
                    tu.setTotalTokens(usage.getTotalTokens() > 0 ? (int) usage.getTotalTokens() : null);
                    tu.setInputText(inputText);
                    tu.setOutputText(outputText);
                    repo.save(tu);
                    log.debug("Token: profile={} node={} turn={} model={} prompt={} completion={} total={}",
                            tu.getProfileName(), tu.getNodeName(), tu.getTurn(), tu.getModelName(),
                            tu.getPromptTokens(), tu.getCompletionTokens(), tu.getTotalTokens());
                    if (outputText != null) {
                        log.debug("LLM output (first 500 chars): {}",
                            outputText.length() > 500 ? outputText.substring(0, 500) : outputText);
                    }
                } catch (Exception e) {
                    log.warn("Failed to persist token usage: {}", e.getMessage());
                }
            }
        }
        return response;
    }

    /**
     * 说明执行顺序。0 会位于默认 ToolCallingAdvisor 之后，从而记录工具循环中的每次模型调用。
     *
     * @return 优先级顺序。
     */
    @Override
    public int getOrder() {
        return 0;
    }

    /**
     * 返回 Advisor 名称，便于日志和链路追踪。
     *
     * @return 固定名称 {@code token-usage-advisor}。
     */
    @Override
    public String getName() {
        return "token-usage-advisor";
    }
}
