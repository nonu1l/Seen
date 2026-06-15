package com.nonu1l.media.model.dto;

import java.util.List;

/**
 * 设置页读取/保存后的响应。
 */
public record SettingsResponse(List<SettingsGroup> groups, boolean applied) {

    public record SettingsGroup(String key, String label, List<SettingItem> settings) {
    }

    public record SettingItem(
            String key,
            String label,
            Object value,
            String type,
            boolean sensitive
    ) {
    }
}
