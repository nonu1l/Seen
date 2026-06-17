package com.nonu1l.media.agent;

import com.nonu1l.media.model.dto.AiStreamEventDTO;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Agent 运行监听器工厂，提供 no-op 与 SSE 转发两种默认实现。
 */
public final class AgentRunEvents {

    private static final AgentRunListener NOOP = new AgentRunListener() {
        @Override
        public void status(String message) {
        }

        @Override
        public void error(String message) {
        }
    };

    private AgentRunEvents() {
    }

    /**
     * 返回不产生副作用的监听器，供非流式接口复用 Agent 链路。
     *
     * @return 空监听器
     */
    public static AgentRunListener noop() {
        return NOOP;
    }

    /**
     * 创建将 Agent 事件转为 {@link AiStreamEventDTO} 的监听器。
     *
     * @param sender SSE 事件发送函数
     * @return 可用于单轮流式对话的监听器
     */
    public static AgentRunListener streaming(Consumer<AiStreamEventDTO> sender) {
        Objects.requireNonNull(sender, "sender must not be null");
        return new StreamingAgentRunListener(sender);
    }

    private static final class StreamingAgentRunListener implements AgentRunListener {
        private final Consumer<AiStreamEventDTO> sender;

        private StreamingAgentRunListener(Consumer<AiStreamEventDTO> sender) {
            this.sender = sender;
        }

        @Override
        public void status(String message) {
            if (message != null && !message.isBlank()) {
                sender.accept(AiStreamEventDTO.status(message));
            }
        }

        @Override
        public void error(String message) {
            if (message != null && !message.isBlank()) {
                sender.accept(AiStreamEventDTO.error(message));
            }
        }
    }
}
