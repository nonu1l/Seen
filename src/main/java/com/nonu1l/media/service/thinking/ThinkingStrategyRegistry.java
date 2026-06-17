package com.nonu1l.media.service.thinking;

import com.nonu1l.media.service.SettingsService;
import org.springframework.stereotype.Service;

import java.util.Comparator;
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
     * 注入所有策略并按优先级排序。
     *
     * @param strategies Spring 管理的思考策略列表
     */
    public ThinkingStrategyRegistry(List<ThinkingStrategy> strategies) {
        this.strategies = strategies.stream()
                .sorted(Comparator.comparingInt(ThinkingStrategy::order))
                .toList();
    }

    /**
     * 按当前运行时配置解析最合适的策略。
     *
     * @param setting 当前 AI 运行配置
     * @return 命中的 provider 策略
     */
    public ThinkingStrategy resolve(SettingsService.AiRuntimeSetting setting) {
        return strategies.stream()
                .filter(strategy -> strategy.supports(setting))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No thinking strategy registered"));
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
}
