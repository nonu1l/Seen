package com.nonu1l.media.controller;

import com.nonu1l.media.model.dto.AppConfigDTO;
import com.nonu1l.media.service.SettingsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 暴露首页启动时需要的轻量运行配置。
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
     * @return 包含 AI 开关与 Bangumi 代理地址的只读配置 DTO。
     */
    @GetMapping("/api/app-config")
    public AppConfigDTO getConfig() {
        return new AppConfigDTO(
                settingsService.getBoolean(SettingsService.AI_ENABLED),
                settingsService.getString(SettingsService.BANGUMI_PROXY)
        );
    }
}
