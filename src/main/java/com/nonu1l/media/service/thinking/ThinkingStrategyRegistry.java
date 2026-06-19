package com.nonu1l.media.service.thinking;

import com.nonu1l.media.service.SettingsService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 统一选择 provider 思考策略，避免业务代码直接判断各家 OpenAI-compatible 差异。
 */
@Service
public class ThinkingStrategyRegistry {

    private final List<ThinkingStrategy> strategies;

    /**
     * 创建包含内置策略的兜底注册器，主要用于热更新后旧 bean 未重新注入新依赖的场景。
     *
     * @return 默认策略注册器
     */
    public static ThinkingStrategyRegistry defaultRegistry() {
        return new ThinkingStrategyRegistry(List.of(
                new OpenAiThinkingStrategy(),
                new DeepSeekThinkingStrategy(),
                new GlmThinkingStrategy(),
                new KimiThinkingStrategy(),
                new MiniMaxThinkingStrategy(),
                new MiMoThinkingStrategy(),
                new CustomThinkingStrategy()
        ));
    }

    /**
     * 注入所有策略。解析时会先匹配具体 provider，再使用兜底策略。
     *
     * @param strategies Spring 管理的思考策略列表
     */
    public ThinkingStrategyRegistry(List<ThinkingStrategy> strategies) {
        this.strategies = List.copyOf(strategies);
    }

    /**
     * 按当前运行时配置解析最合适的策略。
     *
     * @param setting 当前 AI 运行配置
     * @return 命中的 provider 策略
     */
    public ThinkingStrategy resolve(SettingsService.AiRuntimeSetting setting) {
        return strategies.stream()
                .filter(strategy -> !strategy.fallback())
                .filter(strategy -> strategy.supports(setting))
                .findFirst()
                .or(() -> strategies.stream().filter(ThinkingStrategy::fallback).findFirst())
                .orElseThrow(() -> new IllegalStateException("No thinking strategy registered"));
    }

    /**
     * 解析当前配置对应的 provider 展示名。
     *
     * @param setting 当前 AI 运行配置
     * @return 命中策略的 provider 名称
     */
    public String providerName(SettingsService.AiRuntimeSetting setting) {
        return resolve(setting).providerName();
    }

    /**
     * 生成最终请求 extraBody。
     *
     * @param setting 当前 AI 运行配置
     * @param mode 业务希望使用的思考模式
     * @return 可传给 Spring AI OpenAiChatOptions.extraBody 的 map
     */
    public Map<String, Object> extraBody(SettingsService.AiRuntimeSetting setting, ThinkingMode mode) {
        return resolve(setting).extraBody(setting, mode);
    }

    /**
     * 使用当前 provider 策略清理模型正文，去除混入 assistant content 的推理标签。
     *
     * @param setting 当前 AI 运行配置
     * @param content 模型回复正文
     * @return 清理后的正文
     */
    public String cleanAssistantContent(SettingsService.AiRuntimeSetting setting, String content) {
        return resolve(setting).cleanAssistantContent(content);
    }
}
