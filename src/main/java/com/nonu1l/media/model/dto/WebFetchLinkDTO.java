package com.nonu1l.media.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * fetchWeb 从 HTML 页面中提取的可访问链接。
 *
 * @param id 页面正文中的链接编号。
 * @param text 链接可见文本。
 * @param url 解析为绝对地址后的链接。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WebFetchLinkDTO(
        int id,
        String text,
        String url
) {
}
