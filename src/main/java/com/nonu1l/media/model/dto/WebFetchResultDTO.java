package com.nonu1l.media.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * AI 网页抓取工具的结构化返回，保留 HTTP 状态与失败原因。
 *
 * @param ok 是否成功取得可用文本。
 * @param url 最终访问或请求的 URL。
 * @param status HTTP 状态码；未发出请求时为 0。
 * @param contentType 响应内容类型。
 * @param title HTML 标题。
 * @param text 清洗后的正文文本。
 * @param truncated 文本是否被截断。
 * @param error 失败原因。
 * @param hint 给 Agent 的下一步建议。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WebFetchResultDTO(
        boolean ok,
        String url,
        int status,
        String contentType,
        String title,
        String text,
        boolean truncated,
        String error,
        String hint
) {
}
