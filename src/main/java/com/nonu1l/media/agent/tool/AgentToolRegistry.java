package com.nonu1l.media.agent.tool;

import com.nonu1l.media.config.TokenUsageAdvisor;
import com.nonu1l.media.service.SettingsService;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 轻量 Agent 工具注册器：复用 Spring AI 官方 @Tool 解析，只负责工具过滤和 token 节点包装。
 */
@Component
public class AgentToolRegistry {

    private static final String SEARCH_WEB = "searchWeb";

    private final SettingsService settingsService;

    /**
     * @param settingsService 设置服务，用于判断搜索工具是否可暴露
     */
    public AgentToolRegistry(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    /**
     * 将 Spring AI 官方工具对象转换为可传给 ChatClient 的 ToolCallback，并统一包裹 token 节点。
     *
     * @param returnNode 工具调用结束后恢复的节点名
     * @param toolBeans 带 @Tool 方法的 Spring Bean
     * @return 已过滤并包裹的工具回调
     */
    public Object[] select(String returnNode, Object... toolBeans) {
        return selectLimited(returnNode, Integer.MAX_VALUE, null, toolBeans);
    }

    /**
     * 按工具名白名单和单轮预算选择工具；用于受控 Runner 隔离工具能力。
     *
     * @param returnNode 工具调用结束后恢复的节点名
     * @param maxToolCalls 本轮最多允许的工具调用次数
     * @param allowedToolNames 允许暴露的工具名；为空表示不过滤名称
     * @param toolBeans 带 @Tool 方法的 Spring Bean
     * @return 已过滤、带预算保护和 token 节点追踪的工具回调
     */
    public Object[] selectLimited(String returnNode, int maxToolCalls,
                                  Collection<String> allowedToolNames, Object... toolBeans) {
        ToolCallback[] callbacks = MethodToolCallbackProvider.builder()
                .toolObjects(toolBeans != null ? toolBeans : new Object[0])
                .build()
                .getToolCallbacks();
        Set<String> allowed = allowedToolNames == null || allowedToolNames.isEmpty()
                ? Set.of()
                : Set.copyOf(allowedToolNames);
        AtomicInteger callCount = new AtomicInteger();
        int limit = maxToolCalls > 0 ? maxToolCalls : Integer.MAX_VALUE;
        return Arrays.stream(callbacks)
                .filter(callback -> isEnabled(callback, allowed))
                .map(callback -> new TokenTrackingToolCallback(callback, returnNode, callCount, limit))
                .toArray(Object[]::new);
    }

    /**
     * @return 当前设置允许暴露 searchWeb 时返回 true。
     */
    public boolean isWebSearchEnabled() {
        return settingsService.isWebSearchProviderEnabled();
    }

    private boolean isEnabled(ToolCallback callback, Set<String> allowedToolNames) {
        String name = callback.getToolDefinition().name();
        boolean nameAllowed = allowedToolNames == null || allowedToolNames.isEmpty() || allowedToolNames.contains(name);
        return nameAllowed && (!SEARCH_WEB.equals(name) || isWebSearchEnabled());
    }

    private record TokenTrackingToolCallback(
            ToolCallback delegate,
            String returnNode,
            AtomicInteger callCount,
            int maxToolCalls
    ) implements ToolCallback {

        @NotNull
        @Override
        public ToolDefinition getToolDefinition() {
            return delegate.getToolDefinition();
        }

        @NotNull
        @Override
        public ToolMetadata getToolMetadata() {
            return delegate.getToolMetadata();
        }

        @NotNull
        @Override
        public String call(@NotNull String toolInput) {
            if (!tryAcquireBudget()) {
                return budgetExceededMessage();
            }
            setToolNode();
            try {
                return delegate.call(toolInput);
            } finally {
                restoreNode();
            }
        }

        @NotNull
        @Override
        public String call(@NotNull String toolInput, ToolContext toolContext) {
            if (!tryAcquireBudget()) {
                return budgetExceededMessage();
            }
            setToolNode();
            try {
                return delegate.call(toolInput, toolContext);
            } finally {
                restoreNode();
            }
        }

        private boolean tryAcquireBudget() {
            return callCount.incrementAndGet() <= maxToolCalls;
        }

        private String budgetExceededMessage() {
            String toolName = getToolDefinition().name();
            return """
                    {"ok":false,"error":"tool budget exceeded","hint":"本轮已达到工具调用上限，请停止继续调用工具，直接基于已有结果回复用户。","tool":"%s"}
                    """.formatted(toolName).trim();
        }

        private void setToolNode() {
            TokenUsageAdvisor.setCurrentNode("tool-" + getToolDefinition().name());
        }

        private void restoreNode() {
            if (returnNode != null && !returnNode.isBlank()) {
                TokenUsageAdvisor.setCurrentNode(returnNode);
            }
        }
    }
}
