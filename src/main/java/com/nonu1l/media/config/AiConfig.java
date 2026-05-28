package com.nonu1l.media.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 给 Spring AI 使用的 RestClient 挂上拦截器，对 DeepSeek 请求注入 thinking={type:disabled}。
 */
@Configuration
public class AiConfig {

    @Bean
    public RestClientCustomizer deepSeekThinkingDisableCustomizer(ObjectMapper objectMapper) {
        return restClientBuilder -> restClientBuilder
                .requestInterceptor(new DeepSeekThinkingDisableInterceptor(objectMapper));
    }
}
