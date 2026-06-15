package com.nonu1l.media.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 通用配置：跨域策略与静态资源映射。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * 配置 API 跨域规则：允许前端开发域名访问并携带凭证。
     *
     * @param registry 拦截器/映射注册表。
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                /*开发临时放行CROS问题*/
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
}
