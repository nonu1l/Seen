package com.nonu1l.media.model.dto;

/**
 * URL 抓取工具的结构化返回结果，供 LLM 判断页面是否可用及是否需要继续检索。
 *
 * @param url 最终访问的 URL
 * @param status HTTP 状态码；请求未发出时为 0
 * @param contentType 响应 Content-Type
 * @param title HTML 标题，非 HTML 内容为空
 * @param text 清洗后的正文或 JSON 文本
 * @param truncated 返回文本是否被截断
 * @param error 失败原因；成功时为空
 */
public record FetchUrlResultDTO(
        String url,
        int status,
        String contentType,
        String title,
        String text,
        boolean truncated,
        String error
) {
}
