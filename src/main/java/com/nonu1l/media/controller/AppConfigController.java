package com.nonu1l.media.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 暴露应用运行时开关配置给前端使用，如 AI 功能是否启用。
 */
@RestController
public class AppConfigController {

    private final boolean aiEnabled;

    /**
     * 创建控制器并注入 AI 功能开关。
     *
     * @param aiEnabled 是否允许启用 AI 相关接口，来自配置项 {@code seen.ai.enabled}。
     */
    public AppConfigController(@Value("${seen.ai.enabled:true}") boolean aiEnabled) {
        this.aiEnabled = aiEnabled;
    }

    /**
     * 读取应用运行配置。
     *
     * @return 包含 {@code aiEnabled} 标记的只读配置结果。
     */
    @GetMapping("/api/app-config")
    public Map<String, Object> getConfig() {
        return Map.of("aiEnabled", aiEnabled);
    }
}
