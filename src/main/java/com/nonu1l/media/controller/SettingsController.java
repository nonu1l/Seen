package com.nonu1l.media.controller;

import com.nonu1l.media.model.dto.AiProviderSettingRequest;
import com.nonu1l.media.model.dto.AiProviderSettingResponse;
import com.nonu1l.media.model.dto.SettingsResponse;
import com.nonu1l.media.model.dto.SettingsTestRequests;
import com.nonu1l.media.model.dto.SettingsTestResponse;
import com.nonu1l.media.model.dto.UpdateSettingsRequest;
import com.nonu1l.media.service.SettingsService;
import com.nonu1l.media.service.SettingsTestService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 设置页后端接口，负责读取、保存 AI 接入配置和连接测试。
 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService settingsService;
    private final SettingsTestService settingsTestService;

    public SettingsController(SettingsService settingsService,
                              SettingsTestService settingsTestService) {
        this.settingsService = settingsService;
        this.settingsTestService = settingsTestService;
    }

    @GetMapping
    public SettingsResponse getSettings() {
        return settingsService.getSettingsResponse();
    }

    @PutMapping
    public SettingsResponse updateSettings(@RequestBody UpdateSettingsRequest request) {
        return settingsService.updateSettings(request != null ? request.settings() : Map.of());
    }

    @PutMapping("/ai-profile")
    public AiProviderSettingResponse updateAiProfile(@RequestBody AiProviderSettingRequest request) {
        return settingsService.updateAiProviderSetting(request);
    }

    @PostMapping("/ai-profile/test")
    public SettingsTestResponse testAiProfile(@RequestBody SettingsTestRequests.AiTestRequest request) {
        return settingsTestService.testAiProfile(request);
    }

    @PostMapping("/test-search")
    public SettingsTestResponse testSearch(@RequestBody SettingsTestRequests.SearchTestRequest request) {
        return settingsTestService.testSearch(request);
    }

    @PostMapping("/test-bangumi")
    public SettingsTestResponse testBangumi(@RequestBody SettingsTestRequests.BangumiTestRequest request) {
        return settingsTestService.testBangumi(request);
    }
}
