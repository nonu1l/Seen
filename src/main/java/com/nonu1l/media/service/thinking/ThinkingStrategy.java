package com.nonu1l.media.service.thinking;

import com.nonu1l.media.service.SettingsService;

import java.util.Map;
import java.util.Optional;

/**
 * Provider 级思考模式策略，负责按 AI Base URL 识别 provider，并把 {@link ThinkingMode} 翻译成请求扩展参数。
 */
public interface ThinkingStrategy {

    /**
     * 判断当前策略是否适用于指定运行时配置。
     *
     * @param setting 当前 AI 运行配置
     * @return 适用时返回 true
     */
    boolean supports(SettingsService.AiRuntimeSetting setting);

    /**
     * 当前策略对应的 provider 名称，用于 token 记录和设置页诊断展示。
     *
     * @return provider 标识名称
     */
    default String providerName() {
        return "custom";
    }

    /**
     * 标记兜底策略。兜底策略只在没有具体 provider 命中时使用。
     *
     * @return 是否为兜底策略
     */
    default boolean fallback() {
        return false;
    }

    /**
     * 构建 OpenAI-compatible 请求的额外 body 字段。
     *
     * @param setting 当前 AI 运行配置
     * @param mode 应用内部思考模式
     * @return 需要注入的 extraBody；不需要注入时返回空 map
     */
    Map<String, Object> extraBody(SettingsService.AiRuntimeSetting setting, ThinkingMode mode);

    /**
     * 当前策略的默认思考模式。
     *
     * @param setting 当前 AI 运行配置
     * @return 默认思考模式
     */
    default ThinkingMode defaultMode(SettingsService.AiRuntimeSetting setting) {
        return ThinkingMode.DISABLED;
    }

    /**
     * 预留响应解析入口，后续需要展示或保存 reasoning 时可在 provider 策略内处理。
     *
     * @param rawResponse provider 原始响应或中间结构
     * @return 提取到的 reasoning 内容
     */
    default Optional<String> extractReasoningContent(Object rawResponse) {
        return Optional.empty();
    }

    /**
     * 预留回复清理入口，后续可用于去除 provider 混入正文的思考标签。
     *
     * @param content 模型回复正文
     * @return 清理后的正文
     */
    default String cleanAssistantContent(String content) {
        return content;
    }

}
