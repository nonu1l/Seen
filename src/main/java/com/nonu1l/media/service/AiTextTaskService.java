package com.nonu1l.media.service;

import com.nonu1l.media.config.TokenUsageAdvisor;
import com.nonu1l.media.service.thinking.ThinkingMode;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 封装不带工具调用的 LLM 文本任务，统一处理提示词、思考模式、重试和正文清理。
 *
 * <p>设置页思考模式为 default 时尊重单次任务的 thinking 参数；设置为 enabled/disabled 时会强制覆盖。</p>
 */
@Service
public class AiTextTaskService {

    private final AiChatClientFactory chatClientFactory;
    private final SettingsService settingsService;

    /**
     * 创建文本型 LLM 任务服务。
     *
     * @param chatClientFactory AI 客户端工厂，用于获取当前 ChatClient 并清理模型输出
     * @param settingsService 设置服务，用于读取文本任务思考模式覆盖
     */
    public AiTextTaskService(AiChatClientFactory chatClientFactory,
                             SettingsService settingsService) {
        this.chatClientFactory = chatClientFactory;
        this.settingsService = settingsService;
    }

    /**
     * 创建一次文本任务的链式构建器。
     *
     * @return 文本任务构建器
     */
    public TaskBuilder task() {
        return new TaskBuilder();
    }

    /**
     * 文本型 LLM 任务构建器；每个实例只建议执行一次 call。
     */
    public class TaskBuilder {

        private String node;
        private String systemPrompt;
        private String userPrompt;
        private ThinkingMode thinkingMode;
        private int maxAttempts = 1;
        private Consumer<String> cleanedContentListener;

        /**
         * 设置 Token 用量统计节点名。
         *
         * @param node 节点名，可为空
         * @return 当前构建器
         */
        public TaskBuilder node(String node) {
            this.node = node;
            return this;
        }

        /**
         * 设置系统提示词。
         *
         * @param systemPrompt 系统提示词，可为空
         * @return 当前构建器
         */
        public TaskBuilder system(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        /**
         * 设置用户提示词；调用 call 前必须设置。
         *
         * @param userPrompt 用户提示词
         * @return 当前构建器
         */
        public TaskBuilder user(String userPrompt) {
            this.userPrompt = userPrompt;
            return this;
        }

        /**
         * 设置本次调用的思考模式；设置页为 default 时生效，设置页强制开启/关闭时会被覆盖。
         *
         * @param thinkingMode 思考模式
         * @return 当前构建器
         */
        public TaskBuilder thinking(ThinkingMode thinkingMode) {
            this.thinkingMode = thinkingMode;
            return this;
        }

        /**
         * 用布尔值设置是否启用思考模式，便于调用方按开关传参；设置页强制开启/关闭时会被覆盖。
         *
         * @param enabled true 表示启用，false 表示禁用
         * @return 当前构建器
         */
        public TaskBuilder thinkingEnabled(boolean enabled) {
            this.thinkingMode = enabled ? ThinkingMode.ENABLED : ThinkingMode.DISABLED;
            return this;
        }

        /**
         * 设置最大尝试次数；小于 1 时按 1 次处理。
         *
         * @param maxAttempts 最大尝试次数
         * @return 当前构建器
         */
        public TaskBuilder maxAttempts(int maxAttempts) {
            this.maxAttempts = Math.max(1, maxAttempts);
            return this;
        }

        /**
         * 注册清理后正文监听器，适合记录调试日志或保存原始分析结果。
         *
         * @param listener 监听器，可为空
         * @return 当前构建器
         */
        public TaskBuilder onCleanedContent(Consumer<String> listener) {
            this.cleanedContentListener = listener;
            return this;
        }

        /**
         * 执行文本任务并返回清理后的模型正文。
         *
         * @return 清理后的正文
         */
        public String call() {
            return call(Function.identity());
        }

        /**
         * 执行文本任务，并用回调把清理后的正文转换为调用方需要的结果。
         *
         * <p>当模型调用或回调解析抛出异常时，会按 maxAttempts 重试；全部失败后抛出最后一次异常。</p>
         *
         * @param resultHandler 清理后正文的处理回调
         * @param <T> 返回结果类型
         * @return 回调处理后的结果
         */
        public <T> T call(Function<String, T> resultHandler) {
            if (userPrompt == null || userPrompt.isBlank()) {
                throw new IllegalArgumentException("user prompt is required");
            }
            Function<String, T> handler = resultHandler != null ? resultHandler : content -> null;
            RuntimeException lastError = null;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    if (node != null && !node.isBlank()) {
                        TokenUsageAdvisor.setCurrentNode(node);
                    }
                    String content = currentClient().prompt()
                            .system(systemPrompt != null ? systemPrompt : "")
                            .user(userPrompt)
                            .call()
                            .content();
                    String cleaned = chatClientFactory.cleanAssistantContent(content);
                    if (cleanedContentListener != null) {
                        cleanedContentListener.accept(cleaned);
                    }
                    return handler.apply(cleaned);
                } catch (RuntimeException e) {
                    lastError = e;
                } catch (Exception e) {
                    lastError = new IllegalStateException(e);
                }
            }
            throw lastError != null ? lastError : new IllegalStateException("AI text task failed");
        }

        private ChatClient currentClient() {
            ThinkingMode effectiveMode = settingsService.currentAiTextTaskThinkingOverride()
                    .orElse(thinkingMode);
            return chatClientFactory.currentClient(effectiveMode);
        }
    }
}
