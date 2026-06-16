package com.nonu1l.media.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 通用配置：跨域、静态资源映射与 AI 开关拦截器。
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
     * 配置 API 跨域规则：允许前端开发域名访问并携带凭证。
     *
     * @param registry 跨域注册表。
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(
                        "http://localhost:5173",
                        "http://127.0.0.1:5173",
                        "http://192.168.*.*:5173",
                        "http://10.*.*.*:5173",
                        "http://172.*.*.*:5173")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    /**
     * 注册本地静态资源路径映射。
     *
     * @param registry 资源处理器注册表。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");
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
