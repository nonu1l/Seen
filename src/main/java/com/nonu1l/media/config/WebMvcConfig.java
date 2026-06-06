package com.nonu1l.media.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AiFeatureGuard aiFeatureGuard;

    public WebMvcConfig(AiFeatureGuard aiFeatureGuard) {
        this.aiFeatureGuard = aiFeatureGuard;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(aiFeatureGuard)
                .addPathPatterns("/api/conversation/**");
    }
}
