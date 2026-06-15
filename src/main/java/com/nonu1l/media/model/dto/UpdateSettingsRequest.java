package com.nonu1l.media.model.dto;

import java.util.Map;

/**
 * 设置保存请求。
 */
public record UpdateSettingsRequest(Map<String, Object> settings) {
}
