package com.nonu1l.media.service.thinking;

import com.nonu1l.media.service.SettingsService;

import java.util.Map;
import java.util.Optional;

/**
 * Provider 级思考模式策略，负责把应用内部的 {@link ThinkingMode} 翻译成请求扩展参数。
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

    /**
     * 策略优先级，数值越小越优先。
     *
     * @return 优先级数值
     */
    default int order() {
        return 100;
    }
}
