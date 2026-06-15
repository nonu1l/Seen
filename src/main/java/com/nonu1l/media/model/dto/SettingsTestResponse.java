package com.nonu1l.media.model.dto;

import java.util.Map;

/**
 * 设置页连接测试响应。
 */
public record SettingsTestResponse(boolean ok, String message, long elapsedMs, Map<String, Object> details) {
}
