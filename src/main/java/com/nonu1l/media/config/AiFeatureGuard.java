package com.nonu1l.media.config;

import com.nonu1l.media.service.SettingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 会话 API 访问拦截器：当 AI 功能开关关闭时，直接返回 403。
 */
@Component
public class AiFeatureGuard implements HandlerInterceptor {

    private final SettingsService settingsService;

    /**
     * 注入运行时开关配置。
     *
     * @param settingsService 设置读取服务。
     */
    public AiFeatureGuard(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    /**
     * 在请求进入控制器前做 AI 开关检查。
     *
     * @param request  当前 HTTP 请求。
     * @param response 当前 HTTP 响应，用于写入拒绝提示。
     * @param handler  匹配到的处理器对象。
     * @return 开关开启返回 true；关闭则返回 false 并终止后续处理。
     * @throws Exception 写入响应出错时抛出。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (settingsService.getBoolean(SettingsService.AI_ENABLED)) return true;
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"AI feature is disabled\"}");
        return false;
    }
}
