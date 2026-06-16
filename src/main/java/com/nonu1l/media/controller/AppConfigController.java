package com.nonu1l.media.controller;

import com.nonu1l.media.service.SettingsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 暴露应用运行时开关配置给前端使用，如 AI 功能是否启用。
 */


/**
 * TODO 这个可以合并到 SettingsController.java
 */
@RestController
public class AppConfigController {

    private final SettingsService settingsService;

    /**
     * 创建控制器并注入设置服务。
     *
     * @param settingsService 设置读取服务。
     */
    public AppConfigController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    /**
     * 读取应用运行配置。
     *
     * @return 包含 {@code aiEnabled} 标记的只读配置结果。
     */
    @GetMapping("/api/app-config")
    public Map<String, Object> getConfig() {
        return Map.of("aiEnabled", settingsService.getBoolean(SettingsService.AI_ENABLED));
    }
}
