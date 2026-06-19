package com.nonu1l.media.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 在线观看候选地址。
 *
 * @param title 搜索结果标题
 * @param url 候选观看地址
 * @param source 来源域名
 * @param sourceType 来源类型，当前简化片源搜索不再填充
 * @param confidence 标题抽取置信度
 * @param note 页面内容筛选说明
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WatchSourceItemDTO(
        String title,
        String url,
        String source,
        String sourceType,
        double confidence,
        String note
) {
}
