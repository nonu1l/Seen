package com.nonu1l.media.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;

/**
 * Web 搜索结果条目（@Tool 返回）。
 *
 * @param title 标题。
 * @param snippet 摘要内容。
 * @param url 链接地址。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WebSearchItemDTO(String title, String snippet, String url) implements Serializable {}
