package com.nonu1l.media.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC 拦截器配置，统一挂载 AI 开关拦截器。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AiFeatureGuard aiFeatureGuard;

    /**
     * 注入 AI 功能开关拦截器。
     *
     * @param aiFeatureGuard AI 开关拦截器。
     */
    public WebMvcConfig(AiFeatureGuard aiFeatureGuard) {
        this.aiFeatureGuard = aiFeatureGuard;
    }

    /**
     * 仅对会话接口应用 AI 开关校验，禁止未授权请求访问 AI 功能。
     *
     * @param registry 拦截器注册器。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(aiFeatureGuard)
                .addPathPatterns("/api/conversation/**");
    }
}
