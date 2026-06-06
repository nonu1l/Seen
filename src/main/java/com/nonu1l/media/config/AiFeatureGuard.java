package com.nonu1l.media.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AiFeatureGuard implements HandlerInterceptor {

    private final boolean aiEnabled;

    public AiFeatureGuard(@Value("${seen.ai.enabled:true}") boolean aiEnabled) {
        this.aiEnabled = aiEnabled;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (aiEnabled) return true;
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"AI feature is disabled\"}");
        return false;
    }
}
