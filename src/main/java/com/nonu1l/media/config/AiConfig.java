package com.nonu1l.media.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nonu1l.media.repository.TokenUsageRepository;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 相关配置：ChatClient 定制、token 用量记录 Advisor。
 */
@Configuration
public class AiConfig {

    @Bean
    public RestClientCustomizer deepSeekThinkingDisableCustomizer(ObjectMapper objectMapper) {
        return restClientBuilder -> restClientBuilder
                .requestInterceptor(new DeepSeekThinkingDisableInterceptor(objectMapper));
    }

    @Bean
    public TokenUsageAdvisor tokenUsageAdvisor(TokenUsageRepository repo) {
        return new TokenUsageAdvisor(repo);
    }
}
