package com.nonu1l.media.config;

import com.nonu1l.media.model.entity.TokenUsage;
import com.nonu1l.media.repository.TokenUsageRepository;
import com.nonu1l.media.service.SettingsService;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;
import reactor.core.publisher.Flux;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Spring AI 调用拦截器：记录每次 LLM 调用产生的 token 用量到数据库，并提供会话级上下文注入能力。
 */
public class TokenUsageAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger log = LoggerFactory.getLogger(TokenUsageAdvisor.class);
    private static final ThreadLocal<Long> currentSession = new ThreadLocal<>();
    private static final ThreadLocal<String> currentRequest = new ThreadLocal<>();
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
        currentRequest.remove();
        currentNode.remove();
        currentTurn.remove();
    }

    /**
     * 设置当前 AI 请求 ID，用于把多次模型调用归属到同一轮 Agent 执行。
     *
     * @param requestId 请求 ID。
     */
    public static void setRequestId(String requestId) {
        currentRequest.set(requestId);
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
        UsageContext context = captureContext(request);

        ChatClientResponse response = chain.nextCall(request);
        persistUsage(response, context);
        return response;
    }

    /**
     * 在流式调用完成后聚合最终响应并记录 token 用量。
     *
     * @param request 原始聊天请求。
     * @param chain   下游流式调用链。
     * @return 透传给上游的流式响应。
     */
    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        UsageContext context = captureContext(request);
        Flux<ChatClientResponse> responses = chain.nextStream(request);
        return new ChatClientMessageAggregator().aggregateChatClientResponse(
                responses,
                response -> persistUsage(response, context)
        );
    }

    private UsageContext captureContext(ChatClientRequest request) {
        return new UsageContext(
                request.prompt().getContents(),
                currentSession.get(),
                currentRequest.get(),
                currentNode.get(),
                currentTurn.get(),
                settingSupplier.get()
        );
    }

    private void persistUsage(ChatClientResponse response, UsageContext context) {
        if (!settingsService.getBoolean(SettingsService.AI_TOKEN_USAGE_ENABLED)) {
            return;
        }

        var chatResponse = response.chatResponse();
        if (chatResponse != null && chatResponse.getMetadata() != null) {
            Usage usage = chatResponse.getMetadata().getUsage();
            if (usage != null) {
                try {
                    String model = chatResponse.getMetadata().getModel();
                    String outputText = chatResponse.getResult() != null
                            && chatResponse.getResult().getOutput() != null
                            ? chatResponse.getResult().getOutput().getText()
                            : "";
                    TokenUsage tu = new TokenUsage();
                    tu.setSessionId(context.sessionId());
                    tu.setRequestId(context.requestId());
                    tu.setNodeName(context.nodeName());
                    tu.setTurn(context.turn());
                    tu.setProfileId(context.setting().id());
                    tu.setProfileName(profileName(context.setting()));
                    tu.setModelName(model != null ? model : "unknown");
                    tu.setPromptTokens(usage.getPromptTokens() > 0 ? (int) usage.getPromptTokens() : null);
                    tu.setCompletionTokens(usage.getCompletionTokens() > 0 ? (int) usage.getCompletionTokens() : null);
                    tu.setTotalTokens(usage.getTotalTokens() > 0 ? (int) usage.getTotalTokens() : null);
                    tu.setNativeCachedTokens(extractNativeCachedTokens(usage.getNativeUsage()));
                    tu.setInputText(context.inputText());
                    tu.setOutputText(outputText);
                    repo.save(tu);
                    log.debug("Token: profile={} node={} turn={} model={} prompt={} completion={} total={} nativeCached={}",
                            tu.getProfileName(), tu.getNodeName(), tu.getTurn(), tu.getModelName(),
                            tu.getPromptTokens(), tu.getCompletionTokens(), tu.getTotalTokens(),
                            tu.getNativeCachedTokens());
                    if (outputText != null) {
                        log.debug("LLM output (first 500 chars): {}",
                            outputText.length() > 500 ? outputText.substring(0, 500) : outputText);
                    }
                } catch (Exception e) {
                    log.warn("Failed to persist token usage: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * 从 provider 原始 usage 中提取官方 prompt cache 命中数。
     *
     * <p>不同 provider SDK 可能暴露为 Map、Optional 或 record 风格方法，因此这里只读
     * prompt_tokens_details.cached_tokens，不做本地估算。</p>
     *
     * @param nativeUsage Spring AI 暴露的 provider 原始 usage 对象。
     * @return 官方 cached tokens；没有返回时为 {@code null}。
     */
    private Long extractNativeCachedTokens(Object nativeUsage) {
        Object usage = unwrapOptional(nativeUsage);
        if (usage == null) {
            return null;
        }

        Object details = readValue(usage, "prompt_tokens_details", "promptTokensDetails", "getPromptTokensDetails");
        Object cachedTokens = readValue(unwrapOptional(details), "cached_tokens", "cachedTokens", "getCachedTokens");
        Long value = asPositiveLong(unwrapOptional(cachedTokens));
        return value != null && value > 0 ? value : null;
    }

    private Object readValue(Object source, String... names) {
        if (source == null) {
            return null;
        }
        Object unwrapped = unwrapOptional(source);
        if (unwrapped == null) {
            return null;
        }
        if (unwrapped instanceof Map<?, ?> map) {
            for (String name : names) {
                if (map.containsKey(name)) {
                    return map.get(name);
                }
            }
        }
        for (String name : names) {
            try {
                Method method = unwrapped.getClass().getMethod(name);
                return method.invoke(unwrapped);
            } catch (ReflectiveOperationException | SecurityException ignored) {
                // Try the next known accessor shape.
            }
        }
        return null;
    }

    private Object unwrapOptional(Object value) {
        if (value instanceof Optional<?> optional) {
            return optional.orElse(null);
        }
        return value;
    }

    private Long asPositiveLong(Object value) {
        if (value instanceof Number number) {
            long longValue = number.longValue();
            return longValue > 0 ? longValue : null;
        }
        if (value instanceof String text) {
            try {
                long parsed = Long.parseLong(text);
                return parsed > 0 ? parsed : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String profileName(SettingsService.AiRuntimeSetting setting) {
        String baseUrl = setting != null ? setting.baseUrl() : "";
        if (baseUrl == null || baseUrl.isBlank()) {
            return "anthropic-compatible";
        }
        try {
            java.net.URI uri = java.net.URI.create(baseUrl);
            String host = uri.getHost();
            return host == null || host.isBlank() ? "anthropic-compatible" : host;
        } catch (Exception ignored) {
            return "anthropic-compatible";
        }
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

    private record UsageContext(
            String inputText,
            Long sessionId,
            String requestId,
            String nodeName,
            Integer turn,
            SettingsService.AiRuntimeSetting setting
    ) {
    }
}
