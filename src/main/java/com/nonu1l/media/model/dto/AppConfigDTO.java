package com.nonu1l.media.model.dto;

/**
 * 首页启动所需的轻量应用配置 DTO。
 *
 * @param aiEnabled AI 功能是否启用
 * @param bangumiProxy Bangumi API 代理地址
 */
public record AppConfigDTO(
        boolean aiEnabled,
        String bangumiProxy
) {
}
