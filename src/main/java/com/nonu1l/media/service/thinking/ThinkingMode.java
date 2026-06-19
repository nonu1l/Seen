package com.nonu1l.media.service.thinking;

/**
 * 统一描述应用内部的思考模式意图，由 provider 策略翻译为具体 OpenAI-compatible 扩展参数。
 */
public enum ThinkingMode {
    /**
     * 尽量关闭模型的思考内容输出，优先保证结构化回复稳定。
     */
    DISABLED,

    /**
     * 尽量开启模型的思考能力；仅在 provider 明确支持时注入参数。
     */
    ENABLED,
}
