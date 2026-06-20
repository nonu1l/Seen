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
        ToolCallback[] callbacks = MethodToolCallbackProvider.builder()
                .toolObjects(toolBeans != null ? toolBeans : new Object[0])
                .build()
                .getToolCallbacks();
        return Arrays.stream(callbacks)
                .filter(this::isEnabled)
                .map(callback -> new TokenTrackingToolCallback(callback, returnNode))
                .toArray(Object[]::new);
    }

    /**
     * @return 当前设置允许暴露 searchWeb 时返回 true。
     */
    public boolean isWebSearchEnabled() {
        return settingsService.isWebSearchProviderEnabled();
    }

    /**
     * @return Agent prompt 中对网络工具的动态说明。
     */
//    public String webToolGuidance() {
//        if (isWebSearchEnabled()) {
//            return """
//                    - searchWeb 的 error 只描述当前搜索源事实：API key missing / empty response / search failed 通常是 provider 或配置问题，不要反复换关键词；no results / no organic results 可改写关键词重试 1 次；仍失败再向用户说明没有找到可靠结果。
//                    """.strip();
//        }
//        return "- 当前未启用搜索源，不要使用 searchWeb；如用户给出明确 URL，可调用 fetchWeb 读取公开页面。";
//    }

//    /**
//     * @return 分析/问答场景中可提示给模型的网络工具列表。
//     */
//    public String analysisToolList() {
//        return isWebSearchEnabled() ? "searchWeb 或 fetchWeb" : "fetchWeb";
//    }

    /**
     * @return 网络工具失败后的动态处理规则。
     */
    public String webFailureGuidance() {
        if (isWebSearchEnabled()) {
            return "2. searchWeb / fetchWeb 返回 ok=false 时，按 error 判断是否重试：搜索源或访问异常不要反复重试，明确无结果时可改写关键词重试 1 次；多次失败后直接说明资料源访问失败。";
        }
        return "2. fetchWeb 返回 ok=false 时，不要反复重试同一 URL；直接说明资料源访问失败。";
    }

    private boolean isEnabled(ToolCallback callback) {
        String name = callback.getToolDefinition().name();
        return !SEARCH_WEB.equals(name) || isWebSearchEnabled();
    }

    private record TokenTrackingToolCallback(ToolCallback delegate, String returnNode) implements ToolCallback {

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
                setToolNode();
                try {
                    return delegate.call(toolInput, toolContext);
                } finally {
                    restoreNode();
                }
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
