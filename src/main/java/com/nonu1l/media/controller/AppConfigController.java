package com.nonu1l.media.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AppConfigController {

    private final boolean aiEnabled;

    public AppConfigController(@Value("${seen.ai.enabled:true}") boolean aiEnabled) {
        this.aiEnabled = aiEnabled;
    }

    @GetMapping("/api/app-config")
    public Map<String, Object> getConfig() {
        return Map.of("aiEnabled", aiEnabled);
    }
}
