package com.nonu1l.media.config;

import com.nonu1l.media.repository.TokenUsageRepository;
import com.nonu1l.media.service.SettingsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 基础设施配置：注册 Token 用量统计 Advisor。
 */
@Configuration
public class AiConfig {

    /**
     * 注册 token 用量统计 Advisor，便于在 ChatClient 调用时落库记录。
     *
     * @param repo token 持久化仓库。
     * @param settingsService 设置读取服务。
     * @return token 用量 Advisor 实例。
     */
    @Bean
    public TokenUsageAdvisor tokenUsageAdvisor(TokenUsageRepository repo,
                                               SettingsService settingsService) {
        return new TokenUsageAdvisor(repo, settingsService);
    }
}
