package com.nonu1l.media.service;

import com.nonu1l.media.config.TokenUsageAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 统一封装 LLM 调用链路，区分无工具文本任务和带工具副作用的 Agent 单次调用。
 *
 * <p>设置页思考模式为 default 时尊重单次任务的 thinking 参数；设置为 enabled/disabled 时会强制覆盖。</p>
 */
@Service
public class AiChatCallService {

    private final AiChatClientFactory chatClientFactory;
    private final SettingsService settingsService;

    /**
     * 创建 LLM 调用服务。
     *
     * @param chatClientFactory AI 客户端工厂，用于获取当前 ChatClient
     * @param settingsService 设置服务，用于读取文本任务思考模式覆盖
     */
    public AiChatCallService(AiChatClientFactory chatClientFactory,
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
     * 创建一次带工具调用的 Agent 调用构建器。
     *
     * @return Agent 调用构建器
     */
    public AgentBuilder agent() {
        return new AgentBuilder();
    }

    /**
     * 文本型 LLM 任务构建器；每个实例只建议执行一次 call。
     */
    public class TaskBuilder {

        private String node;
        private String systemPrompt;
        private Map<String, Object> systemParams = Map.of();
        private String userPrompt;
        private Map<String, Object> userParams = Map.of();
        private AiThinkingMode thinkingMode;
        private int maxAttempts = 1;
        private Consumer<String> contentListener;

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
            this.systemParams = Map.of();
            return this;
        }

        /**
         * 设置带模板参数的系统提示词。
         *
         * @param systemPrompt 系统提示词模板，可为空
         * @param params 模板参数，可为空
         * @return 当前构建器
         */
        public TaskBuilder system(String systemPrompt, Map<String, Object> params) {
            this.systemPrompt = systemPrompt;
            this.systemParams = params != null ? params : Map.of();
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
            this.userParams = Map.of();
            return this;
        }

        /**
         * 设置带模板参数的用户提示词。
         *
         * @param userPrompt 用户提示词模板
         * @param params 模板参数，可为空
         * @return 当前构建器
         */
        public TaskBuilder user(String userPrompt, Map<String, Object> params) {
            this.userPrompt = userPrompt;
            this.userParams = params != null ? params : Map.of();
            return this;
        }

        /**
         * 设置本次调用的思考模式；设置页为 default 时生效，设置页强制开启/关闭时会被覆盖。
         *
         * @param thinkingMode 思考模式
         * @return 当前构建器
         */
        public TaskBuilder thinking(AiThinkingMode thinkingMode) {
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
            this.thinkingMode = enabled ? AiThinkingMode.ENABLED : AiThinkingMode.DISABLED;
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
         * 注册模型正文监听器，适合记录调试日志或保存原始分析结果。
         *
         * @param listener 监听器，可为空
         * @return 当前构建器
         */
        public TaskBuilder onContent(Consumer<String> listener) {
            this.contentListener = listener;
            return this;
        }

        /**
         * 执行文本任务并返回模型正文。
         *
         * @return 模型正文
         */
        public String call() {
            return call(Function.identity());
        }

        /**
         * 执行文本任务，并用回调把模型正文转换为调用方需要的结果。
         *
         * <p>当模型调用或回调解析抛出异常时，会按 maxAttempts 重试；全部失败后抛出最后一次异常。</p>
         * <p>允许回调函数传入，如果回调函数失败也会进行重试</p>
         *
         * @param resultHandler 模型正文的处理回调
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
                            .system(s -> s.text(systemPrompt != null ? systemPrompt : "").params(systemParams))
                            .user(u -> u.text(userPrompt).params(userParams))
                            .call()
                            .content();
                    if (contentListener != null) {
                        contentListener.accept(content);
                    }
                    return handler.apply(content);
                } catch (RuntimeException e) {
                    lastError = e;
                } catch (Exception e) {
                    lastError = new IllegalStateException(e);
                }
            }
            throw lastError != null ? lastError : new IllegalStateException("AI text task failed");
        }

        private ChatClient currentClient() {
            AiThinkingMode effectiveMode = settingsService.currentAiTextTaskThinkingOverride()
                    .orElse(thinkingMode);
            return chatClientFactory.currentClient(effectiveMode);
        }
    }

    /**
     * 带工具调用的 Agent 构建器；工具可能产生副作用，因此只提供单次调用，不做任务级重试。
     */
    public class AgentBuilder {

        private String node;
        private String systemPrompt;
        private Map<String, Object> systemParams = Map.of();
        private String userPrompt;
        private Map<String, Object> userParams = Map.of();
        private AiThinkingMode thinkingMode;
        private Object[] tools = new Object[0];

        /**
         * 设置 Token 用量统计节点名。
         *
         * @param node 节点名，可为空
         * @return 当前构建器
         */
        public AgentBuilder node(String node) {
            this.node = node;
            return this;
        }

        /**
         * 设置系统提示词。
         *
         * @param systemPrompt 系统提示词，可为空
         * @return 当前构建器
         */
        public AgentBuilder system(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            this.systemParams = Map.of();
            return this;
        }

        /**
         * 设置带模板参数的系统提示词。
         *
         * @param systemPrompt 系统提示词模板，可为空
         * @param params 模板参数，可为空
         * @return 当前构建器
         */
        public AgentBuilder system(String systemPrompt, Map<String, Object> params) {
            this.systemPrompt = systemPrompt;
            this.systemParams = params != null ? params : Map.of();
            return this;
        }

        /**
         * 设置用户提示词；调用 callOnceResponse 前必须设置。
         *
         * @param userPrompt 用户提示词
         * @return 当前构建器
         */
        public AgentBuilder user(String userPrompt) {
            this.userPrompt = userPrompt;
            this.userParams = Map.of();
            return this;
        }

        /**
         * 设置带模板参数的用户提示词。
         *
         * @param userPrompt 用户提示词模板
         * @param params 模板参数，可为空
         * @return 当前构建器
         */
        public AgentBuilder user(String userPrompt, Map<String, Object> params) {
            this.userPrompt = userPrompt;
            this.userParams = params != null ? params : Map.of();
            return this;
        }

        /**
         * 设置本次 Agent 调用的思考模式；为空时使用设置页当前默认模式。
         *
         * @param thinkingMode 思考模式
         * @return 当前构建器
         */
        public AgentBuilder thinking(AiThinkingMode thinkingMode) {
            this.thinkingMode = thinkingMode;
            return this;
        }

        /**
         * 设置本次 Agent 可调用的 Spring AI 工具对象或 ToolCallback。
         *
         * @param tools 工具对象列表
         * @return 当前构建器
         */
        public AgentBuilder tools(Object... tools) {
            this.tools = tools != null ? tools : new Object[0];
            return this;
        }

        /**
         * 执行一次带工具的 Agent 调用并返回完整响应；不会因解析失败重试整轮工具链路。
         *
         * @return 完整 ChatResponse，包含文本、thinking、tool 内容块等元数据
         */
        public ChatResponse callOnceResponse() {
            if (userPrompt == null || userPrompt.isBlank()) {
                throw new IllegalArgumentException("user prompt is required");
            }
            if (node != null && !node.isBlank()) {
                TokenUsageAdvisor.setCurrentNode(node);
            }
            return currentAgentClient().prompt()
                    .system(s -> s.text(systemPrompt != null ? systemPrompt : "").params(systemParams))
                    .user(u -> u.text(userPrompt).params(userParams))
                    .tools(tools)
                    .call()
                    .chatResponse();
        }

        private ChatClient currentAgentClient() {
            return thinkingMode != null ? chatClientFactory.currentClient(thinkingMode) : chatClientFactory.currentClient();
        }
    }
}
